package com.omits.social_api.topic;

import com.omits.social_api.signal.model.SignalSource;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * A subject the user writes about, and the per-source queries that find signals for it.
 *
 * <p>This is what turns the radar from "what is popular" into "what is popular in the things
 * I write about". {@link #queries} is pushed upstream to each source rather than filtered
 * client-side: dev.to takes a tag, GitHub takes search qualifiers, Hacker News takes Algolia
 * search terms. A source absent from the map is simply not polled for this topic — which is
 * why this is a map and not five nullable columns.
 *
 * <p>{@link #enabled} is the off switch that keeps a topic's configuration around: a disabled
 * topic stops being polled but its name still resolves, so the signals it already produced
 * stay labelled instead of losing their category.
 *
 * <p>Straight CRUD, no state machine — unlike {@code Draft}, nothing here has an order to
 * move through, so every field except the timestamps is freely mutable.
 */
@Entity
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic {

    /** Matches the {@code name} column's length; the service rejects anything longer. */
    public static final int MAX_NAME_LENGTH = 120;

    /** Matches the {@code topic_queries.query} column's length. */
    public static final int MAX_QUERY_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false)
    private boolean enabled;

    /**
     * Per-source queries, keyed by the source they are sent to. An {@link ElementCollection}
     * rather than an entity: a query has no identity of its own, is meaningless outside its
     * topic, and dies with it — which the {@code ON DELETE CASCADE} on {@code topic_queries}
     * mirrors at the schema level.
     *
     * <p>Lazy, so listing topics does not fan out into a query per row. Readers go through
     * {@link TopicRepository}'s fetch-joining finders, which load the map in the same query.
     */
    @ElementCollection
    @CollectionTable(name = "topic_queries", joinColumns = @JoinColumn(name = "topic_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "source", length = 20)
    @Column(name = "query", nullable = false, length = MAX_QUERY_LENGTH)
    private Map<SignalSource, String> queries = new EnumMap<>(SignalSource.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Topic(String name, boolean enabled, Map<SignalSource, String> queries) {
        this.name = name;
        this.enabled = enabled;
        replaceQueries(queries);
    }

    /**
     * Replaces every query in one go. Mutates the managed map rather than assigning a new
     * one: Hibernate tracks the collection instance it handed out, and swapping it for a
     * fresh map orphans that tracking.
     */
    void replaceQueries(Map<SignalSource, String> replacement) {
        this.queries.clear();
        if (replacement != null) {
            this.queries.putAll(replacement);
        }
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
