package com.omits.social_api.signal;

import com.omits.social_api.signal.model.SignalSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A normalized item from a content source, used as drafting input.
 *
 * <p>Identity is {@code (source, externalId)}, not the surrogate {@link #id}: the radar
 * re-polls the same listings on every run, so the same item is seen many times. A repeat
 * sighting refreshes the item rather than inserting a duplicate — see
 * {@link SignalService#ingest}.
 *
 * <p>That split is what decides which fields are mutable. {@link #score} and
 * {@link #fetchedAt} change as an item rises and falls in its source's ranking, and
 * {@link #topic} and {@link #url} can be edited at the source after first publication.
 * {@link #source}, {@link #externalId}, and {@link #createdAt} are the identity and its
 * first-seen timestamp, so they are fixed once written.
 *
 * <p>{@link #rawPayload} keeps the source's original JSON. Normalization is lossy by
 * design — {@link #score} flattens incomparable native units (HN points, dev.to
 * reactions, GitHub stars) onto one scale — so the untouched payload is retained for
 * anything a later phase wants that this shape does not carry.
 */
@Entity
@Table(name = "signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SignalSource source;

    @Column(name = "external_id", nullable = false, length = 255, updatable = false)
    private String externalId;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, columnDefinition = "text")
    private String topic;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false)
    private int score;

    @Setter(AccessLevel.PACKAGE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Setter(AccessLevel.PACKAGE)
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Signal(SignalSource source, String externalId, String topic, String url, int score,
                  String rawPayload, Instant fetchedAt) {
        this.source = source;
        this.externalId = externalId;
        this.topic = topic;
        this.url = url;
        this.score = score;
        this.rawPayload = rawPayload;
        this.fetchedAt = fetchedAt;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
