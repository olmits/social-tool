package com.omits.social_api.account;

import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.dto.ConnectAccountCommand;
import com.omits.social_api.account.exception.AccountNotFoundException;
import com.omits.social_api.account.exception.DuplicateAccountException;
import com.omits.social_api.account.exception.RedditAccountLimitException;
import com.omits.social_api.account.mastodon.MastodonAccountDetails;
import com.omits.social_api.account.mastodon.MastodonAccountDetailsRepository;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final MastodonAccountDetailsRepository mastodonAccountDetailsRepository =
            mock(MastodonAccountDetailsRepository.class);
    private final CredentialStore credentialStore = mock(CredentialStore.class);

    private final AccountService accountService =
            new AccountService(accountRepository, mastodonAccountDetailsRepository, credentialStore);

    @Test
    void connectsBlueskyAccount() {
        when(credentialStore.store("app-password")).thenReturn("social-api/accounts/secret-ref");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = accountService.connect(
                new ConnectAccountCommand(Platform.BLUESKY, "user.bsky.social", "app-password", null));

        assertThat(account.getPlatform()).isEqualTo(Platform.BLUESKY);
        assertThat(account.getHandle()).isEqualTo("user.bsky.social");
        assertThat(account.getCredentialRef()).isEqualTo("social-api/accounts/secret-ref");
        verify(mastodonAccountDetailsRepository, never()).save(any());
    }

    @Test
    void connectsMastodonAccountAndSavesInstanceDetails() {
        when(credentialStore.store("token")).thenReturn("social-api/accounts/secret-ref");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findActiveMastodonAccount(eq("user"), eq("mastodon.social"), eq(AccountStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        accountService.connect(new ConnectAccountCommand(Platform.MASTODON, "user", "token", "mastodon.social"));

        verify(mastodonAccountDetailsRepository).save(any(MastodonAccountDetails.class));
    }

    @Test
    void rejectsMastodonAccountWithoutInstance() {
        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.MASTODON, "user", "token", " ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialStore, never()).store(anyString());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsNonMastodonAccountWithInstance() {
        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.BLUESKY, "user", "app-password", "mastodon.social")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialStore, never()).store(anyString());
    }

    @Test
    void rejectsBlankHandle() {
        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.BLUESKY, " ", "app-password", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialStore, never()).store(anyString());
    }

    @Test
    void rejectsBlankCredentialValue() {
        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.BLUESKY, "user.bsky.social", " ", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(credentialStore, never()).store(anyString());
    }

    @Test
    void rejectsSecondActiveRedditAccount() {
        when(accountRepository.existsByPlatformAndStatus(Platform.REDDIT, AccountStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.REDDIT, "user", "token", null)))
                .isInstanceOf(RedditAccountLimitException.class);

        verify(credentialStore, never()).store(anyString());
    }

    @Test
    void rejectsDuplicateActiveHandleForNonMastodonPlatform() {
        when(accountRepository.existsByPlatformAndHandleAndStatus(Platform.BLUESKY, "user.bsky.social", AccountStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.BLUESKY, "user.bsky.social", "app-password", null)))
                .isInstanceOf(DuplicateAccountException.class);

        verify(credentialStore, never()).store(anyString());
    }

    @Test
    void rejectsDuplicateActiveMastodonHandleAndInstance() {
        Account existing = new Account(Platform.MASTODON, "user", "social-api/accounts/existing-ref");
        when(accountRepository.findActiveMastodonAccount("user", "mastodon.social", AccountStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                accountService.connect(new ConnectAccountCommand(Platform.MASTODON, "user", "token", "mastodon.social")))
                .isInstanceOf(DuplicateAccountException.class);

        verify(credentialStore, never()).store(anyString());
    }

    @Test
    void disconnectMarksStatusAndDeletesCredential() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(Platform.BLUESKY, "user.bsky.social", "social-api/accounts/secret-ref");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        accountService.disconnect(accountId);

        verify(credentialStore).delete("social-api/accounts/secret-ref");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISCONNECTED);
    }

    @Test
    void disconnectThrowsWhenAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.disconnect(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getThrowsWhenAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.get(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void mastodonInstancesByAccountIdReturnsMapKeyedByAccountId() {
        UUID mastodonAccountId = UUID.randomUUID();
        UUID blueskyAccountId = UUID.randomUUID();
        MastodonAccountDetails details = new MastodonAccountDetails(mastodonAccountId, "mastodon.social");
        when(mastodonAccountDetailsRepository.findAllById(List.of(mastodonAccountId, blueskyAccountId)))
                .thenReturn(List.of(details));

        Map<UUID, String> instances =
                accountService.mastodonInstancesByAccountId(List.of(mastodonAccountId, blueskyAccountId));

        assertThat(instances).containsExactly(Map.entry(mastodonAccountId, "mastodon.social"));
    }
}
