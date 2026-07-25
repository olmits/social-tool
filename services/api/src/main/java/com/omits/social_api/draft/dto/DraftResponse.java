package com.omits.social_api.draft.dto;

import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.Draft;
import com.omits.social_api.draft.model.DraftStatus;

import java.time.Instant;
import java.util.UUID;

public record DraftResponse(UUID id, UUID accountId, UUID signalId, Platform platform, String content,
                            String affiliateLinks, DraftStatus status, boolean aiGenerated,
                            boolean disclosureIncluded, Instant scheduledAt, String remoteId,
                            String failureReason, Instant createdAt, Instant updatedAt) {

    public static DraftResponse from(Draft draft) {
        return new DraftResponse(draft.getId(), draft.getAccountId(), draft.getSignalId(), draft.getPlatform(),
                draft.getContent(), draft.getAffiliateLinks(), draft.getStatus(), draft.isAiGenerated(),
                draft.isDisclosureIncluded(), draft.getScheduledAt(), draft.getRemoteId(),
                draft.getFailureReason(), draft.getCreatedAt(), draft.getUpdatedAt());
    }
}
