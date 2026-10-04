package com.omits.social_api.voice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * How the author sounds — the third input to AI drafting, alongside the signal and the target
 * platform.
 *
 * <p>{@link #instructions} is one free-text field rather than a set of structured ones (tone,
 * length, emoji policy). It reaches the system prompt verbatim, so anything the author can say
 * in English works, and nothing they want to say has to wait for a column. Pasting two or
 * three of their own posts in there is a few-shot example set at no schema cost.
 *
 * <p>Deliberately platform-agnostic. Voice is how you sound; how long a post may be is the
 * platform's business, and the generation slice reads that from
 * {@code Platform.maxPostLength()}. A per-platform profile would be three near-identical rows
 * differing only in a number the prompt already supplies.
 *
 * <p>{@link #enabled} mirrors {@code Topic.enabled}: a disabled profile keeps its text but
 * drops out of the selector, so a voice can be retired without losing what it said.
 *
 * <p>Straight CRUD, no state machine — like {@code Topic} and unlike {@code Draft}, nothing
 * here has an order to move through.
 */
@Entity
@Table(name = "voice_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceProfile {

    /** Matches the {@code name} column's length; the service rejects anything longer. */
    public static final int MAX_NAME_LENGTH = 120;

    /**
     * A service-level cap on a {@code TEXT} column, the same arrangement {@code Draft.content}
     * uses. Generous enough for a paragraph of guidance plus a few example posts, and low
     * enough that a pasted article cannot quietly become the system prompt.
     */
    public static final int MAX_INSTRUCTIONS_LENGTH = 4_000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, columnDefinition = "text")
    private String instructions;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false)
    private boolean enabled;

    /**
     * Named {@code defaultProfile} rather than {@code isDefault} because {@code default} is a
     * Java keyword and the generated {@code isDefault()} reads as a question about the object
     * rather than about the flag. The column keeps the natural SQL name.
     *
     * <p>At most one profile may hold this, enforced by the partial unique index
     * {@code uq_voice_profiles_default}. See {@link VoiceProfileService#update} for why the
     * old default has to be cleared before a new one is set.
     */
    @Setter(AccessLevel.PACKAGE)
    @Column(name = "is_default", nullable = false)
    private boolean defaultProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VoiceProfile(String name, String instructions, boolean enabled, boolean defaultProfile) {
        this.name = name;
        this.instructions = instructions;
        this.enabled = enabled;
        this.defaultProfile = defaultProfile;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
