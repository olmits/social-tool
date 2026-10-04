package com.omits.social_api.voice.dto;

import com.omits.social_api.voice.VoiceProfile;

import java.time.Instant;
import java.util.UUID;

/**
 * @param isDefault whether this profile is pre-selected in the generate dialog. At most one
 *                  profile in the list carries it, and none does on a fresh install.
 */
public record VoiceProfileResponse(UUID id, String name, String instructions, boolean enabled,
                                   boolean isDefault, Instant createdAt, Instant updatedAt) {

    public static VoiceProfileResponse from(VoiceProfile profile) {
        return new VoiceProfileResponse(profile.getId(), profile.getName(),
                profile.getInstructions(), profile.isEnabled(), profile.isDefaultProfile(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
