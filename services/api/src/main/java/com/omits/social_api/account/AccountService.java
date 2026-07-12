package com.omits.social_api.account;

import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.exception.AccountNotFoundException;
import com.omits.social_api.account.exception.DuplicateAccountException;
import com.omits.social_api.account.exception.RedditAccountLimitException;
import com.omits.social_api.account.mastodon.MastodonAccountDetails;
import com.omits.social_api.account.mastodon.MastodonAccountDetailsRepository;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.ConnectAccountCommand;
import com.omits.social_api.account.model.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final MastodonAccountDetailsRepository mastodonAccountDetailsRepository;
    private final CredentialStore credentialStore;

    public Account connect(ConnectAccountCommand command) {
        validateInstance(command);
        checkRedditLimit(command);
        checkDuplicate(command);

        String credentialRef = credentialStore.store(command.credentialValue());
        Account account = accountRepository.save(new Account(command.platform(), command.handle(), credentialRef));

        if (command.platform() == Platform.MASTODON) {
            mastodonAccountDetailsRepository.save(new MastodonAccountDetails(account.getId(), command.instance()));
        }

        return account;
    }

    public List<Account> list() {
        return accountRepository.findAll();
    }

    public List<Account> list(Platform platform) {
        return accountRepository.findAll().stream()
                .filter(account -> account.getPlatform() == platform)
                .toList();
    }

    public Account get(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public void disconnect(UUID accountId) {
        Account account = get(accountId);
        credentialStore.delete(account.getCredentialRef());
        account.markDisconnected();
        accountRepository.save(account);
    }

    private static void validateInstance(ConnectAccountCommand command) {
        boolean instanceBlank = command.instance() == null || command.instance().isBlank();
        if (command.platform() == Platform.MASTODON && instanceBlank) {
            throw new IllegalArgumentException("instance is required for Mastodon accounts");
        }
        if (command.platform() != Platform.MASTODON && !instanceBlank) {
            throw new IllegalArgumentException("instance is only applicable to Mastodon accounts");
        }
    }

    private void checkRedditLimit(ConnectAccountCommand command) {
        if (command.platform() == Platform.REDDIT
                && accountRepository.existsByPlatformAndStatus(Platform.REDDIT, AccountStatus.ACTIVE)) {
            throw new RedditAccountLimitException();
        }
    }

    private void checkDuplicate(ConnectAccountCommand command) {
        if (command.platform() == Platform.MASTODON) {
            accountRepository.findActiveMastodonAccount(command.handle(), command.instance(), AccountStatus.ACTIVE)
                    .ifPresent(existing -> {
                        throw new DuplicateAccountException(
                                "Account " + command.handle() + "@" + command.instance() + " is already connected");
                    });
            return;
        }

        boolean exists = accountRepository.existsByPlatformAndHandleAndStatus(
                command.platform(), command.handle(), AccountStatus.ACTIVE);
        if (exists) {
            throw new DuplicateAccountException(
                    "Account " + command.handle() + " on " + command.platform() + " is already connected");
        }
    }
}
