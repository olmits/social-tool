package com.omits.social_api.account;

import com.omits.social_api.account.dto.AccountResponse;
import com.omits.social_api.account.dto.ConnectAccountCommand;
import com.omits.social_api.account.model.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> connect(@RequestBody ConnectAccountCommand command) {
        Account account = accountService.connect(command);
        String instance = command.platform() == Platform.MASTODON ? command.instance() : null;
        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(AccountResponse.from(account, instance));
    }

    @GetMapping
    public List<AccountResponse> list(@RequestParam(required = false) Platform platform) {
        List<Account> accounts = platform == null ? accountService.list() : accountService.list(platform);
        Map<UUID, String> instances = accountService.mastodonInstancesByAccountId(
                accounts.stream().map(Account::getId).toList());
        return accounts.stream().map(a -> AccountResponse.from(a, instances.get(a.getId()))).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        Account account = accountService.get(id);
        String instance = accountService.mastodonInstancesByAccountId(List.of(id)).get(id);
        return AccountResponse.from(account, instance);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnect(@PathVariable UUID id) {
        accountService.disconnect(id);
        return ResponseEntity.noContent().build();
    }
}
