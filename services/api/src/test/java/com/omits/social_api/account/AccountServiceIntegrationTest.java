package com.omits.social_api.account;

import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.dto.ConnectAccountCommand;
import com.omits.social_api.account.mastodon.MastodonAccountDetailsRepository;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@SpringBootTest
class AccountServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("social-api.security.api-key", () -> "test-api-key");
    }

    @MockitoBean
    private CredentialStore credentialStore;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MastodonAccountDetailsRepository mastodonAccountDetailsRepository;

    @Test
    void allowsSameHandleOnDifferentMastodonInstances() {
        when(credentialStore.store("token")).thenReturn("social-api/accounts/ref-1", "social-api/accounts/ref-2");

        Account first = accountService.connect(
                new ConnectAccountCommand(Platform.MASTODON, "user", "token", "mastodon.social"));
        Account second = accountService.connect(
                new ConnectAccountCommand(Platform.MASTODON, "user", "token", "fosstodon.org"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(mastodonAccountDetailsRepository.findById(first.getId())).isPresent();
        assertThat(mastodonAccountDetailsRepository.findById(second.getId())).isPresent();
    }

    @Test
    void rejectsDuplicateHandleOnSameNonMastodonPlatformAtDbLevel() {
        when(credentialStore.store("app-password")).thenReturn("social-api/accounts/ref-1", "social-api/accounts/ref-2");

        accountService.connect(new ConnectAccountCommand(Platform.BLUESKY, "user.bsky.social", "app-password", null));

        // Bypass the service-level check to confirm the DB constraint itself holds.
        Account duplicate = new Account(Platform.BLUESKY, "user.bsky.social", "social-api/accounts/ref-2");
        assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAccountCascadesToMastodonAccountDetails() {
        when(credentialStore.store("token")).thenReturn("social-api/accounts/ref-1");

        Account account = accountService.connect(
                new ConnectAccountCommand(Platform.MASTODON, "cascade-user", "token", "mastodon.social"));
        UUID accountId = account.getId();
        assertThat(mastodonAccountDetailsRepository.findById(accountId)).isPresent();

        accountRepository.deleteById(accountId);
        accountRepository.flush();

        assertThat(mastodonAccountDetailsRepository.findById(accountId)).isEmpty();
    }

    @Test
    void disconnectPersistsStatusChange() {
        when(credentialStore.store("app-password")).thenReturn("social-api/accounts/ref-1");

        Account account = accountService.connect(
                new ConnectAccountCommand(Platform.BLUESKY, "disconnect-user.bsky.social", "app-password", null));

        accountService.disconnect(account.getId());

        Optional<Account> reloaded = accountRepository.findById(account.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(AccountStatus.DISCONNECTED);
    }
}
