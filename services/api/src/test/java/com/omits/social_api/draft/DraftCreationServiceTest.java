package com.omits.social_api.draft;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.AccountService;
import com.omits.social_api.account.exception.AccountNotActiveException;
import com.omits.social_api.account.exception.AccountNotFoundException;
import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.dto.CreateDraftCommand;
import com.omits.social_api.draft.exception.PlatformMismatchException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftCreationServiceTest {

    private final AccountService accountService = mock(AccountService.class);
    private final DraftService draftService = mock(DraftService.class);
    private final DraftCreationService draftCreationService =
            new DraftCreationService(accountService, draftService);

    @Test
    void createsDraftForActiveAccountOnMatchingPlatform() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);
        Draft expected = new Draft(accountId, null, Platform.BLUESKY, "hello world", false);
        when(draftService.create(accountId, Platform.BLUESKY, "hello world")).thenReturn(expected);

        Draft draft = draftCreationService.create(
                new CreateDraftCommand(accountId, Platform.BLUESKY, "hello world"));

        assertThat(draft).isSameAs(expected);
    }

    @Test
    void rejectsNullAccountId() {
        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(null, Platform.BLUESKY, "hello")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accountService, never()).get(any());
        verifyNoDraftCreated();
    }

    @Test
    void propagatesAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountService.get(accountId)).thenThrow(new AccountNotFoundException(accountId));

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, Platform.BLUESKY, "hello")))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoDraftCreated();
    }

    @Test
    void rejectsDisconnectedAccount() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY).markDisconnected();

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, Platform.BLUESKY, "hello")))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoDraftCreated();
    }

    @Test
    void rejectsPlatformThatDoesNotMatchTheAccount() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, Platform.MASTODON, "hello")))
                .isInstanceOf(PlatformMismatchException.class);
        verifyNoDraftCreated();
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * A real (ACTIVE) account behind {@code accountService.get(accountId)}. Its own id stays null
     * — JPA assigns that — which only affects exception message text, and the lookup is stubbed
     * by key anyway.
     */
    private Account stubAccount(UUID accountId, Platform platform) {
        Account account = new Account(platform, "user.handle", "social-api/accounts/ref");
        when(accountService.get(accountId)).thenReturn(account);
        return account;
    }

    private void verifyNoDraftCreated() {
        verify(draftService, never()).create(any(), any(), anyString());
    }
}
