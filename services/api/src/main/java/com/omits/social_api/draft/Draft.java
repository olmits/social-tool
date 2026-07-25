package com.omits.social_api.draft;

import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.model.DraftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A unit of content moving through the review pipeline for a single account and platform.
 *
 * <p>The {@link #status} field is the state machine that drives the pipeline:
 * {@code DRAFT -> APPROVED -> SCHEDULED -> PUBLISHED -> FAILED}. Its setter is
 * package-private by design, so only collaborators in this package (notably
 * {@code DraftService}) may transition a draft; the transition rules themselves are
 * enforced there, not on the entity. Nothing else writes {@code drafts.status}.
 *
 * <p>A draft is created in {@code DRAFT} with disclosure not yet included. {@link #content},
 * {@link #affiliateLinks}, and {@link #disclosureIncluded} are editable during review; every
 * other field is fixed at creation. A draft containing an affiliate link must have
 * {@code disclosureIncluded} set to {@code true} before it may be approved (enforced in
 * {@code DraftService.approve()}).
 *
 * <p>{@link #signalId} is nullable: a draft may originate from a trend-radar signal (Phase 2)
 * or be authored from a free-form topic, in which case there is no signal to reference.
 */
@Entity
@Table(name = "drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "signal_id")
    private UUID signalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Setter
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Setter
    @Column(name = "affiliate_links", columnDefinition = "text")
    private String affiliateLinks;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, length = 20)
    private DraftStatus status;

    @Column(name = "ai_generated", nullable = false, updatable = false)
    private boolean aiGenerated;

    @Setter
    @Column(name = "disclosure_included", nullable = false)
    private boolean disclosureIncluded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Draft(UUID accountId, UUID signalId, Platform platform, String content, boolean aiGenerated) {
        this.accountId = accountId;
        this.signalId = signalId;
        this.platform = platform;
        this.content = content;
        this.aiGenerated = aiGenerated;
        this.status = DraftStatus.DRAFT;
        this.disclosureIncluded = false;
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
