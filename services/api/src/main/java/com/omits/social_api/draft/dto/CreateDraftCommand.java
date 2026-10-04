package com.omits.social_api.draft.dto;

import com.omits.social_api.account.model.Platform;

import java.util.UUID;

/**
 * The manual-authoring request: content the author typed themselves.
 *
 * <p>There is deliberately no {@code aiGenerated} flag. Authorship is not something an HTTP
 * caller gets to assert — a draft is marked AI-written only by the generation slice, which goes
 * through {@code DraftCreationService.createGenerated}.
 *
 * @param signalId the radar signal this draft was written about, or null when it came from a
 *                 free-form topic. Optional, and absent from older callers' bodies, which
 *                 deserialize to null and keep their previous behaviour. When present it must
 *                 name a real signal — {@code DraftCreationService} checks, so that a bad id
 *                 is a 404 rather than the 500 a foreign-key violation would produce.
 */
public record CreateDraftCommand(UUID accountId, UUID signalId, Platform platform, String content) {
}
