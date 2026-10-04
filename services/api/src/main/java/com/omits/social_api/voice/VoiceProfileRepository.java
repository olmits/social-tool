package com.omits.social_api.voice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface VoiceProfileRepository extends JpaRepository<VoiceProfile, UUID> {

    /**
     * Case-insensitive, matching the {@code lower(name)} unique index. Checking any other way
     * would let the service accept a name the database then rejects.
     */
    Optional<VoiceProfile> findByNameIgnoreCase(String name);

    /**
     * The profile used when a generate request names none. At most one row can hold the flag
     * ({@code uq_voice_profiles_default}), so this cannot return an arbitrary winner.
     */
    Optional<VoiceProfile> findByDefaultProfileTrue();

    /**
     * Clears the default flag wherever it is set, as one statement.
     *
     * <p>This exists because {@code uq_voice_profiles_default} is a partial unique index: if a
     * new default were set before the old one was cleared, the index would see two true rows
     * and reject the write. Flushing the clear first — hence
     * {@code flushAutomatically} — makes the order the database sees match the order intended.
     * {@code clearAutomatically} then drops the stale managed copies so a caller re-reading a
     * profile in the same transaction does not see the pre-clear value.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VoiceProfile v set v.defaultProfile = false where v.defaultProfile = true")
    void clearDefault();
}
