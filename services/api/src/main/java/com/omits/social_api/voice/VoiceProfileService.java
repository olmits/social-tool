package com.omits.social_api.voice;

import com.omits.social_api.voice.dto.CreateVoiceProfileCommand;
import com.omits.social_api.voice.dto.UpdateVoiceProfileCommand;
import com.omits.social_api.voice.exception.DuplicateVoiceProfileException;
import com.omits.social_api.voice.exception.VoiceProfileNotEnabledException;
import com.omits.social_api.voice.exception.VoiceProfileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the {@code voice_profiles} store: how the author sounds.
 *
 * <p>Straight CRUD, modelled on {@link com.omits.social_api.topic.TopicService} — there is no
 * state machine here, and {@code enabled} is a flag that may be flipped either way any number
 * of times.
 *
 * <p>The one thing that is not plain CRUD is the default flag. {@code uq_voice_profiles_default}
 * is a partial unique index, so at most one row may hold it; switching the default therefore
 * has to clear the old one before setting the new one, within a single transaction. See
 * {@link #makeDefault}.
 */
@Service
@RequiredArgsConstructor
public class VoiceProfileService {

    private static final Comparator<VoiceProfile> BY_NAME =
            Comparator.comparing(VoiceProfile::getName, String.CASE_INSENSITIVE_ORDER);

    private final VoiceProfileRepository voiceProfileRepository;

    @Transactional
    public VoiceProfile create(CreateVoiceProfileCommand command) {
        String name = normalizeName(command.name());
        String instructions = normalizeInstructions(command.instructions());
        requireNameAvailable(name, null);

        boolean enabled = command.enabled() == null || command.enabled();
        boolean makeDefault = Boolean.TRUE.equals(command.isDefault());
        if (makeDefault) {
            voiceProfileRepository.clearDefault();
        }
        return voiceProfileRepository.save(
                new VoiceProfile(name, instructions, enabled, makeDefault));
    }

    @Transactional(readOnly = true)
    public List<VoiceProfile> list() {
        return voiceProfileRepository.findAll().stream().sorted(BY_NAME).toList();
    }

    @Transactional(readOnly = true)
    public VoiceProfile get(UUID voiceProfileId) {
        return voiceProfileRepository.findById(voiceProfileId)
                .orElseThrow(() -> new VoiceProfileNotFoundException(voiceProfileId));
    }

    /**
     * The profile a generate request falls back to when it names none. Empty on a fresh
     * install, and also whenever the author has cleared the flag without setting another —
     * both of which leave the caller to decide what to do, rather than picking a profile for
     * them.
     */
    @Transactional(readOnly = true)
    public Optional<VoiceProfile> findDefault() {
        return voiceProfileRepository.findByDefaultProfileTrue();
    }

    /**
     * Resolves a profile for drafting: it must exist and be switched on.
     *
     * <p>Lives here rather than in the generation slice so that "a usable voice" is defined once,
     * next to the flag that decides it.
     *
     * @throws VoiceProfileNotFoundException   if no such profile
     * @throws VoiceProfileNotEnabledException if it exists but is switched off
     */
    @Transactional(readOnly = true)
    public VoiceProfile getEnabled(UUID voiceProfileId) {
        VoiceProfile profile = get(voiceProfileId);
        if (!profile.isEnabled()) {
            throw new VoiceProfileNotEnabledException(voiceProfileId);
        }
        return profile;
    }

    /**
     * Applies a sparse patch — see {@link UpdateVoiceProfileCommand}. A null field is left
     * alone.
     */
    @Transactional
    public VoiceProfile update(UUID voiceProfileId, UpdateVoiceProfileCommand command) {
        VoiceProfile profile = get(voiceProfileId);

        if (command.name() != null) {
            String name = normalizeName(command.name());
            requireNameAvailable(name, voiceProfileId);
            profile.setName(name);
        }
        if (command.instructions() != null) {
            profile.setInstructions(normalizeInstructions(command.instructions()));
        }
        if (command.enabled() != null) {
            profile.setEnabled(command.enabled());
        }
        if (command.isDefault() != null) {
            makeDefault(profile, command.isDefault());
        }
        return voiceProfileRepository.save(profile);
    }

    /**
     * Deletes the profile. Drafts already written in it are untouched — there is no FK from
     * {@code drafts}, by design, so removing a voice does not disturb what it produced.
     */
    @Transactional
    public void delete(UUID voiceProfileId) {
        if (!voiceProfileRepository.existsById(voiceProfileId)) {
            throw new VoiceProfileNotFoundException(voiceProfileId);
        }
        voiceProfileRepository.deleteById(voiceProfileId);
    }

    /**
     * Moves the default onto {@code profile}, or clears it.
     *
     * <p>The clear-then-set order is load-bearing: {@code uq_voice_profiles_default} indexes
     * only rows where the flag is true, so setting first would momentarily present two true
     * rows and the write would be rejected. The repository's bulk clear flushes before
     * returning, which is what makes the database see the two steps in this order.
     */
    private void makeDefault(VoiceProfile profile, boolean shouldBeDefault) {
        if (!shouldBeDefault) {
            profile.setDefaultProfile(false);
            return;
        }
        if (profile.isDefaultProfile()) {
            return;
        }
        voiceProfileRepository.clearDefault();
        profile.setDefaultProfile(true);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String trimmed = name.strip();
        if (trimmed.length() > VoiceProfile.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be at most %d characters".formatted(VoiceProfile.MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    private static String normalizeInstructions(String instructions) {
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("instructions must not be blank");
        }
        String trimmed = instructions.strip();
        if (trimmed.length() > VoiceProfile.MAX_INSTRUCTIONS_LENGTH) {
            throw new IllegalArgumentException("instructions must be at most %d characters"
                    .formatted(VoiceProfile.MAX_INSTRUCTIONS_LENGTH));
        }
        return trimmed;
    }

    /**
     * @param selfId the profile being renamed, excluded from the check so that saving a
     *               profile under the name it already holds is not a conflict with itself
     */
    private void requireNameAvailable(String name, UUID selfId) {
        voiceProfileRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), selfId)) {
                throw new DuplicateVoiceProfileException(name);
            }
        });
    }
}
