package com.omits.social_api.generation.dto;

import com.omits.social_api.account.model.Platform;

import java.util.UUID;

/**
 * A request to write a draft about a radar signal.
 *
 * <p>Carries no content, by definition, and no {@code aiGenerated} flag — reaching this
 * endpoint is what makes a draft AI-written.
 *
 * @param platform       the target platform. Derivable from the account, and sent anyway for
 *                       the same reason {@code CreateDraftCommand} sends it: a panel bug that
 *                       drafts for the wrong platform should be a 409 rather than silently
 *                       producing a 300-character post for a 500-character account
 * @param voiceProfileId the voice to write in, or null to use the default. Null with no
 *                       default configured is an error, not a neutral fallback — see
 *                       {@code NoVoiceProfileException}
 */
public record GenerateDraftCommand(UUID accountId, UUID signalId, Platform platform,
                                   UUID voiceProfileId) {
}
