package com.omits.social_api.draft;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.AccountService;
import com.omits.social_api.account.exception.AccountNotActiveException;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.dto.CreateDraftCommand;
import com.omits.social_api.draft.exception.PlatformMismatchException;
import com.omits.social_api.signal.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application service composing the {@code account}, {@code signal} and {@code draft} slices:
 * it resolves and validates the target account, and the originating signal when there is one,
 * before delegating to {@link DraftService#create}.
 *
 * <p>The checks live here rather than in {@link DraftService} so the draft slice keeps
 * referencing accounts and signals by id alone and never holds an {@link Account}. This is the
 * codebase's seam for cross-slice orchestration — a domain service should not inject another
 * slice's service or repository.
 *
 * <p>It is also the only way into {@link DraftService#create}: the generation slice goes through
 * {@link #createGenerated} rather than touching {@code DraftService} itself, which keeps
 * "nothing but {@code DraftService} writes {@code drafts.status}" true with one caller to
 * check instead of two.
 */
@Service
@RequiredArgsConstructor
public class DraftCreationService {

    private final AccountService accountService;
    private final SignalService signalService;
    private final DraftService draftService;

    /**
     * Creates a draft for an existing, connected account whose platform matches the draft's.
     *
     * @throws com.omits.social_api.account.exception.AccountNotFoundException if no such account
     * @throws com.omits.social_api.signal.exception.SignalNotFoundException if a signal id was
     *         given and names nothing
     * @throws AccountNotActiveException if the account has been disconnected
     * @throws PlatformMismatchException if the draft targets a different platform than the account
     */
    public Draft create(CreateDraftCommand command) {
        validateAccount(command.accountId(), command.platform());
        requireSignalExists(command.signalId());
        return draftService.create(command.accountId(), command.signalId(), command.platform(),
                command.content(), false);
    }

    /**
     * The AI-authoring entry point: the same account gate, then a draft marked
     * {@code aiGenerated = true} and linked to the signal it was written about.
     *
     * <p>{@code aiGenerated} is fixed by which method you call rather than carried on a
     * command object, so no HTTP caller can claim AI authorship for hand-typed text.
     */
    public Draft createGenerated(UUID accountId, UUID signalId, Platform platform, String content) {
        validateAccount(accountId, platform);
        requireSignalExists(signalId);
        return draftService.create(accountId, signalId, platform, content, true);
    }

    /**
     * Runs the account gate without creating anything.
     *
     * <p>For callers that are about to do expensive work before they have any content to
     * save — the generation slice calls this before the Claude request, so a disconnected
     * account fails in milliseconds rather than after a paid, 90-second round trip.
     */
    public void requireDraftable(UUID accountId, Platform platform) {
        validateAccount(accountId, platform);
    }

    private Account validateAccount(UUID accountId, Platform platform) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        Account account = accountService.get(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(account.getId(), account.getStatus());
        }
        if (account.getPlatform() != platform) {
            throw new PlatformMismatchException(account.getId(), account.getPlatform(), platform);
        }
        return account;
    }

    /**
     * {@code drafts.signal_id} is a real foreign key, so an id naming nothing would otherwise
     * surface as a constraint violation that maps to no handler — a 500 for input the caller
     * could fix. One indexed primary-key read turns it into a 404. Skipped entirely when no
     * signal was given, which is the common case for a hand-written draft.
     */
    private void requireSignalExists(UUID signalId) {
        if (signalId != null) {
            signalService.get(signalId);
        }
    }
}
