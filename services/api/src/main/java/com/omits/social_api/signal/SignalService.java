package com.omits.social_api.signal;

import com.omits.social_api.signal.dto.IngestSignalsCommand;
import com.omits.social_api.signal.dto.IngestSignalsResponse;
import com.omits.social_api.signal.exception.SignalNotFoundException;
import com.omits.social_api.signal.model.SignalSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the {@code signals} store: the generation slice's input pool.
 *
 * <p>Ingest is idempotent. The radar re-polls the same listings on a schedule, so the great
 * majority of every batch is items already stored; a repeat sighting refreshes the item's
 * mutable fields rather than inserting a duplicate. That makes a run safe to repeat, retry,
 * or overlap with another — the pollers need no coordination and no claim step.
 */
@Service
@RequiredArgsConstructor
public class SignalService {

    /** Guards against a malformed or runaway poller filling the table in one call. */
    static final int MAX_BATCH_SIZE = 2_000;

    private final SignalRepository signalRepository;

    /**
     * One signal by id.
     *
     * <p>Added for the generation slice, which needs a signal's title, url and source to build a
     * prompt. Deliberately not exposed as {@code GET /signals/{id}} — there is no reader for
     * that route yet, and {@code SignalResponse} would need its topic name joined on the way
     * out, which is {@code RadarService}'s job rather than this one's.
     */
    @Transactional(readOnly = true)
    public Signal get(UUID signalId) {
        return signalRepository.findById(signalId)
                .orElseThrow(() -> new SignalNotFoundException(signalId));
    }

    /**
     * Stores one radar run's harvest, creating first sightings and refreshing repeats.
     *
     * <p>The batch is deduplicated on {@code (source, externalId)} before any write, last
     * occurrence winning, so a source that lists the same item twice in one response cannot
     * violate the unique index.
     */
    @Transactional
    public IngestSignalsResponse ingest(IngestSignalsCommand command) {
        if (command == null || command.signals() == null) {
            throw new IllegalArgumentException("signals must not be null");
        }
        if (command.signals().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batch of %d exceeds the maximum of %d signals".formatted(command.signals().size(), MAX_BATCH_SIZE));
        }
        command.signals().forEach(SignalService::validate);

        // Collapse in-batch repeats first; the map key mirrors the table's unique index.
        Map<Key, IngestSignalsCommand.Item> incoming = new LinkedHashMap<>();
        for (IngestSignalsCommand.Item item : command.signals()) {
            incoming.put(new Key(item.source(), item.externalId()), item);
        }

        Map<Key, Signal> existing = loadExisting(incoming.keySet());

        List<Signal> toSave = new ArrayList<>(incoming.size());
        int created = 0;
        int updated = 0;
        for (Map.Entry<Key, IngestSignalsCommand.Item> entry : incoming.entrySet()) {
            IngestSignalsCommand.Item item = entry.getValue();
            Signal signal = existing.get(entry.getKey());
            if (signal == null) {
                toSave.add(new Signal(item.source(), item.externalId(), item.title(), item.topicId(),
                        item.url(), item.score(), item.nativeScore(), item.rawPayload().toString(),
                        item.fetchedAt()));
                created++;
            } else {
                signal.setTitle(item.title());
                signal.setTopicId(item.topicId());
                signal.setUrl(item.url());
                signal.setScore(item.score());
                signal.setNativeScore(item.nativeScore());
                signal.setRawPayload(item.rawPayload().toString());
                signal.setFetchedAt(item.fetchedAt());
                toSave.add(signal);
                updated++;
            }
        }
        signalRepository.saveAll(toSave);

        return new IngestSignalsResponse(command.signals().size(), created, updated);
    }

    /**
     * Stored signals, highest score first, narrowed by either filter or neither.
     *
     * <p>Both filters are applied in the query rather than in memory: the panel's default view
     * is a single topic, and the {@code (topic_id, score desc)} index exists to serve exactly
     * that.
     */
    @Transactional(readOnly = true)
    public List<Signal> list(UUID topicId, SignalSource source) {
        if (topicId == null) {
            return source == null
                    ? signalRepository.findAllByOrderByScoreDesc()
                    : signalRepository.findBySourceOrderByScoreDesc(source);
        }
        return source == null
                ? signalRepository.findByTopicIdOrderByScoreDesc(topicId)
                : signalRepository.findByTopicIdAndSourceOrderByScoreDesc(topicId, source);
    }

    /**
     * Loads the stored signals matching {@code keys}, one query per distinct source rather
     * than one per item.
     */
    private Map<Key, Signal> loadExisting(Iterable<Key> keys) {
        Map<SignalSource, List<String>> idsBySource = new LinkedHashMap<>();
        for (Key key : keys) {
            idsBySource.computeIfAbsent(key.source(), s -> new ArrayList<>()).add(key.externalId());
        }
        return idsBySource.entrySet().stream()
                .flatMap(entry -> signalRepository
                        .findBySourceAndExternalIdIn(entry.getKey(), entry.getValue()).stream())
                .collect(Collectors.toMap(
                        signal -> new Key(signal.getSource(), signal.getExternalId()),
                        signal -> signal));
    }

    private static void validate(IngestSignalsCommand.Item item) {
        if (item == null) {
            throw new IllegalArgumentException("signals must not contain null entries");
        }
        if (item.source() == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (item.externalId() == null || item.externalId().isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
        if (item.title() == null || item.title().isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (item.url() == null || item.url().isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (item.score() < 0 || item.score() > 100) {
            throw new IllegalArgumentException(
                    "score must be between 0 and 100 (got %d)".formatted(item.score()));
        }
        // Unlike score, nativeScore has no ceiling — it is whatever the source counted.
        if (item.nativeScore() < 0) {
            throw new IllegalArgumentException(
                    "nativeScore must not be negative (got %d)".formatted(item.nativeScore()));
        }
        // An empty object is a legitimate payload; a missing or JSON-null one is not.
        if (item.rawPayload() == null || item.rawPayload().isNull()) {
            throw new IllegalArgumentException("rawPayload must not be null");
        }
        if (item.fetchedAt() == null) {
            throw new IllegalArgumentException("fetchedAt must not be null");
        }
    }

    /** The table's unique index, as a lookup key. */
    private record Key(SignalSource source, String externalId) {
    }
}
