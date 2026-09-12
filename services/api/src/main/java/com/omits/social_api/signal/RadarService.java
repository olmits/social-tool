package com.omits.social_api.signal;

import com.omits.social_api.signal.dto.IngestSignalsCommand;
import com.omits.social_api.signal.dto.IngestSignalsResponse;
import com.omits.social_api.signal.dto.SignalResponse;
import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.Topic;
import com.omits.social_api.topic.TopicService;
import com.omits.social_api.topic.exception.TopicNotEnabledException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service composing the {@code signal} and {@code topic} slices — the trend radar
 * as the outside world sees it.
 *
 * <p>Two things need both slices and so live here rather than in {@link SignalService}, which
 * references topics by id alone and never holds a {@link Topic}:
 *
 * <ul>
 *   <li><strong>Ingest validation.</strong> A batch naming a topic that does not exist, or one
 *       the user has switched off, is rejected rather than stored.</li>
 *   <li><strong>Read labelling.</strong> Signals go out with their topic's name attached, so
 *       the panel renders a row without a second request and a join of its own.</li>
 * </ul>
 *
 * <p>This is the codebase's seam for cross-slice orchestration, following
 * {@code DraftCreationService}.
 */
@Service
@RequiredArgsConstructor
public class RadarService {

    private final SignalService signalService;
    private final TopicService topicService;

    /**
     * Stores one radar run's harvest after checking every topic it names.
     *
     * <p>Validation is up front and batch-wide: one bad {@code topicId} rejects the whole
     * call rather than storing part of it. The poller retries a failed batch unchanged and
     * ingest is idempotent, so an all-or-nothing failure is recoverable, whereas a partial
     * write leaves nobody able to say what was stored.
     *
     * @throws com.omits.social_api.topic.exception.TopicNotFoundException if a named topic does not exist
     * @throws TopicNotEnabledException if a named topic is switched off
     */
    @Transactional
    public IngestSignalsResponse ingest(IngestSignalsCommand command) {
        if (command != null && command.signals() != null) {
            requireTopicsAcceptSignals(command.signals());
        }
        return signalService.ingest(command);
    }

    /** Stored signals, ranked by score, each labelled with its topic's name. */
    @Transactional(readOnly = true)
    public List<SignalResponse> list(UUID topicId, SignalSource source) {
        List<Signal> signals = signalService.list(topicId, source);
        Map<UUID, String> names = topicNamesFor(signals);
        return signals.stream()
                .map(signal -> SignalResponse.from(signal, topicName(names, signal.getTopicId())))
                .toList();
    }

    private void requireTopicsAcceptSignals(List<IngestSignalsCommand.Item> items) {
        // Distinct ids only: a 2000-item batch typically names a handful of topics, and
        // checking per item would be that many lookups of the same few rows.
        Set<UUID> topicIds = new LinkedHashSet<>();
        for (IngestSignalsCommand.Item item : items) {
            if (item != null && item.topicId() != null) {
                topicIds.add(item.topicId());
            }
        }
        for (UUID topicId : topicIds) {
            Topic topic = topicService.get(topicId);
            if (!topic.isEnabled()) {
                throw new TopicNotEnabledException(topicId);
            }
        }
    }

    /**
     * Names for the topics the given signals reference, as one lookup rather than one per
     * signal. A signal whose topic was deleted between the two reads is simply left unlabelled
     * — the same shape as a signal that never had a topic.
     */
    /**
     * Guards the lookup rather than the map: an untopiced signal has a null id, and
     * {@code Map.of()} throws on a null key rather than answering absent.
     */
    private static String topicName(Map<UUID, String> names, UUID topicId) {
        return topicId == null ? null : names.get(topicId);
    }

    private Map<UUID, String> topicNamesFor(List<Signal> signals) {
        Set<UUID> topicIds = signals.stream()
                .map(Signal::getTopicId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return topicService.namesByIds(topicIds);
    }
}
