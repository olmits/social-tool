package com.omits.social_api.draft;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.AccountService;
import com.omits.social_api.account.exception.AccountNotActiveException;
import com.omits.social_api.account.exception.AccountNotFoundException;
import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.dto.CreateDraftCommand;
import com.omits.social_api.draft.exception.PlatformMismatchException;
import com.omits.social_api.signal.Signal;
import com.omits.social_api.signal.SignalService;
import com.omits.social_api.signal.exception.SignalNotFoundException;
import com.omits.social_api.signal.model.SignalSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DraftCreationServiceTest {

    private final AccountService accountService = mock(AccountService.class);
    private final SignalService signalService = mock(SignalService.class);
    private final DraftService draftService = mock(DraftService.class);
    private final DraftCreationService draftCreationService =
            new DraftCreationService(accountService, signalService, draftService);

    // --- create ---------------------------------------------------------------

    @Test
    void createsDraftForActiveAccountOnMatchingPlatform() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);
        Draft expected = new Draft(accountId, null, Platform.BLUESKY, "hello world", false);
        when(draftService.create(accountId, null, Platform.BLUESKY, "hello world", false))
                .thenReturn(expected);

        Draft draft = draftCreationService.create(
                new CreateDraftCommand(accountId, null, Platform.BLUESKY, "hello world"));

        assertThat(draft).isSameAs(expected);
    }

    @Test
    void recordsTheSignalADraftWasWrittenAbout() {
        UUID accountId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);
        when(signalService.get(signalId)).thenReturn(signal());
        when(draftService.create(accountId, signalId, Platform.BLUESKY, "hello", false))
                .thenReturn(new Draft(accountId, signalId, Platform.BLUESKY, "hello", false));

        Draft draft = draftCreationService.create(
                new CreateDraftCommand(accountId, signalId, Platform.BLUESKY, "hello"));

        assertThat(draft.getSignalId()).isEqualTo(signalId);
    }

    /**
     * {@code drafts.signal_id} is a real FK, so without this check an unknown id surfaces as a
     * constraint violation with no handler — a 500 for input the caller could fix.
     */
    @Test
    void rejectsASignalIdThatNamesNothing() {
        UUID accountId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);
        when(signalService.get(signalId)).thenThrow(new SignalNotFoundException(signalId));

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, signalId, Platform.BLUESKY, "hello")))
                .isInstanceOf(SignalNotFoundException.class);
        verifyNoDraftCreated();
    }

    /** The common case for a hand-written draft; no reason to pay for a lookup. */
    @Test
    void doesNotLookUpASignalWhenNoneWasGiven() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);

        draftCreationService.create(new CreateDraftCommand(accountId, null, Platform.BLUESKY, "hi"));

        verify(signalService, never()).get(any());
    }

    @Test
    void rejectsNullAccountId() {
        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(null, null, Platform.BLUESKY, "hello")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accountService, never()).get(any());
        verifyNoDraftCreated();
    }

    @Test
    void propagatesAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountService.get(accountId)).thenThrow(new AccountNotFoundException(accountId));

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, null, Platform.BLUESKY, "hello")))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoDraftCreated();
    }

    @Test
    void rejectsDisconnectedAccount() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY).markDisconnected();

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, null, Platform.BLUESKY, "hello")))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoDraftCreated();
    }

    @Test
    void rejectsPlatformThatDoesNotMatchTheAccount() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);

        assertThatThrownBy(() -> draftCreationService.create(
                new CreateDraftCommand(accountId, null, Platform.MASTODON, "hello")))
                .isInstanceOf(PlatformMismatchException.class);
        verifyNoDraftCreated();
    }

    // --- createGenerated ------------------------------------------------------

    /** Authorship is fixed by which method is called, so no caller can claim it. */
    @Test
    void createGeneratedMarksTheDraftAiWritten() {
        UUID accountId = UUID.randomUUID();
        UUID signalId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);
        when(signalService.get(signalId)).thenReturn(signal());

        draftCreationService.createGenerated(accountId, signalId, Platform.BLUESKY, "written");

        verify(draftService).create(accountId, signalId, Platform.BLUESKY, "written", true);
    }

    @Test
    void createGeneratedAppliesTheSameAccountGate() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY).markDisconnected();

        assertThatThrownBy(() -> draftCreationService.createGenerated(
                accountId, UUID.randomUUID(), Platform.BLUESKY, "written"))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoDraftCreated();
    }

    // --- requireDraftable -----------------------------------------------------

    /** The whole point: it runs the gate and writes nothing, so generation can call it first. */
    @Test
    void requireDraftableValidatesWithoutCreatingAnything() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);

        draftCreationService.requireDraftable(accountId, Platform.BLUESKY);

        verifyNoDraftCreated();
    }

    @Test
    void requireDraftableRejectsAPlatformMismatch() {
        UUID accountId = UUID.randomUUID();
        stubAccount(accountId, Platform.BLUESKY);

        assertThatThrownBy(() -> draftCreationService.requireDraftable(accountId, Platform.MASTODON))
                .isInstanceOf(PlatformMismatchException.class);
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

    private static Signal signal() {
        return new Signal(SignalSource.HACKER_NEWS, "42", "Shipping Go services", null,
                "https://example.com/go", 90, 312, "{}", java.time.Instant.now());
    }

    private void verifyNoDraftCreated() {
        verify(draftService, never()).create(any(), any(), any(), anyString(), anyBoolean());
    }
}
