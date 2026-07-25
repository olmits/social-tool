package com.omits.social_api.draft;

import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.exception.DisclosureRequiredException;
import com.omits.social_api.draft.exception.DraftNotFoundException;
import com.omits.social_api.draft.exception.InvalidStateTransitionException;
import com.omits.social_api.draft.model.DraftStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the draft state machine. Every transition validates the current status before advancing,
 * and this is the only component permitted to write {@code drafts.status} (the entity's status
 * setter is package-private to enforce that). The single valid sequence is
 * {@code DRAFT -> APPROVED -> SCHEDULED -> PUBLISHED}, with {@code SCHEDULED -> FAILED} as the
 * error branch reported by the Go publisher. Any other transition raises
 * {@link InvalidStateTransitionException}.
 */
@Service
@RequiredArgsConstructor
public class DraftService {

    private final DraftRepository draftRepository;

    /**
     * Creates a new draft in {@link DraftStatus#DRAFT}. This is the manual-authoring path
     * ({@code aiGenerated = false}); AI-generated drafts are created by the drafting slice.
     */
    public Draft create(UUID accountId, Platform platform, String content) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return draftRepository.save(new Draft(accountId, null, platform, content, false));
    }

    /**
     * Transitions {@code DRAFT -> APPROVED}. A draft that carries affiliate links must have
     * {@code disclosureIncluded = true} first, otherwise {@link DisclosureRequiredException}.
     */
    public Draft approve(UUID draftId) {
        Draft draft = get(draftId);
        requireStatus(draft, DraftStatus.DRAFT, DraftStatus.APPROVED);
        if (hasAffiliateLinks(draft) && !draft.isDisclosureIncluded()) {
            throw new DisclosureRequiredException(draftId);
        }
        draft.setStatus(DraftStatus.APPROVED);
        return draftRepository.save(draft);
    }

    /** Transitions {@code APPROVED -> SCHEDULED}, recording when the draft is due. */
    public Draft schedule(UUID draftId, Instant scheduledAt) {
        if (scheduledAt == null) {
            throw new IllegalArgumentException("scheduledAt must not be null");
        }
        Draft draft = get(draftId);
        requireStatus(draft, DraftStatus.APPROVED, DraftStatus.SCHEDULED);
        draft.setScheduledAt(scheduledAt);
        draft.setStatus(DraftStatus.SCHEDULED);
        return draftRepository.save(draft);
    }

    /**
     * Transitions {@code SCHEDULED -> PUBLISHED}, recording the platform's remote id.
     * Called by the Go publisher via the API once a post lands.
     */
    public Draft markPublished(UUID draftId, String remoteId) {
        if (remoteId == null || remoteId.isBlank()) {
            throw new IllegalArgumentException("remoteId must not be blank");
        }
        Draft draft = get(draftId);
        requireStatus(draft, DraftStatus.SCHEDULED, DraftStatus.PUBLISHED);
        draft.setRemoteId(remoteId);
        draft.setStatus(DraftStatus.PUBLISHED);
        return draftRepository.save(draft);
    }

    /**
     * Transitions {@code SCHEDULED -> FAILED}, recording why. Called by the Go publisher via the
     * API when a publish attempt fails.
     */
    public Draft markFailed(UUID draftId, String reason) {
        Draft draft = get(draftId);
        requireStatus(draft, DraftStatus.SCHEDULED, DraftStatus.FAILED);
        draft.setFailureReason(reason);
        draft.setStatus(DraftStatus.FAILED);
        return draftRepository.save(draft);
    }

    public Draft get(UUID draftId) {
        return draftRepository.findById(draftId).orElseThrow(() -> new DraftNotFoundException(draftId));
    }

    /**
     * Lists drafts, optionally narrowed by account and/or status. Both filters are optional;
     * omitting both returns every draft. The account filter keeps listings scoped to the
     * selected account, per the panel's operating model.
     */
    public List<Draft> list(UUID accountId, DraftStatus status) {
        if (accountId != null && status != null) {
            return draftRepository.findByAccountIdAndStatus(accountId, status);
        }
        if (accountId != null) {
            return draftRepository.findByAccountId(accountId);
        }
        if (status != null) {
            return draftRepository.findByStatus(status);
        }
        return draftRepository.findAll();
    }

    private static void requireStatus(Draft draft, DraftStatus expected, DraftStatus target) {
        if (draft.getStatus() != expected) {
            throw new InvalidStateTransitionException(draft.getId(), draft.getStatus(), target);
        }
    }

    private static boolean hasAffiliateLinks(Draft draft) {
        return draft.getAffiliateLinks() != null && !draft.getAffiliateLinks().isBlank();
    }
}
