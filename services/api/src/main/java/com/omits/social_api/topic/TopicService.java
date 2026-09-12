package com.omits.social_api.topic;

import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.dto.CreateTopicCommand;
import com.omits.social_api.topic.dto.UpdateTopicCommand;
import com.omits.social_api.topic.exception.DuplicateTopicException;
import com.omits.social_api.topic.exception.TopicNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the {@code topics} store: what the user writes about, and how each source is queried
 * for it.
 *
 * <p>Straight CRUD. There is no state machine here — {@code enabled} is a flag, not a state,
 * and it may be flipped in either direction any number of times.
 *
 * <p>Reads sort by name in Java rather than in HQL. The finders fetch-join the query map, and
 * an {@code order by} across a fetched collection is the kind of thing that quietly stops
 * being honoured; the list is small enough that sorting it here is free and obviously correct.
 */
@Service
@RequiredArgsConstructor
public class TopicService {

    private static final Comparator<Topic> BY_NAME =
            Comparator.comparing(Topic::getName, String.CASE_INSENSITIVE_ORDER);

    private final TopicRepository topicRepository;

    @Transactional
    public Topic create(CreateTopicCommand command) {
        String name = normalizeName(command.name());
        Map<SignalSource, String> queries = validateQueries(command.queries());
        requireNameAvailable(name, null);

        boolean enabled = command.enabled() == null || command.enabled();
        return topicRepository.save(new Topic(name, enabled, queries));
    }

    @Transactional(readOnly = true)
    public List<Topic> list() {
        return topicRepository.findAllWithQueries().stream().sorted(BY_NAME).toList();
    }

    /** The poller's work list: topics that are switched on, with their queries loaded. */
    @Transactional(readOnly = true)
    public List<Topic> listEnabled() {
        return topicRepository.findEnabledWithQueries().stream().sorted(BY_NAME).toList();
    }

    /**
     * Names for the given topic ids, for labelling rows that reference topics without loading
     * the topics themselves. Deliberately does not fetch-join {@code queries}: callers want the
     * name, and a caller that never touches the map never triggers its load.
     *
     * <p>Ids with no matching topic are absent from the result rather than mapped to null, so
     * a caller cannot tell a deleted topic from one that was never set — which is correct here,
     * since both render the same way.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesByIds(Collection<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return Map.of();
        }
        return topicRepository.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName));
    }

    @Transactional(readOnly = true)
    public Topic get(UUID topicId) {
        return topicRepository.findByIdWithQueries(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    /**
     * Applies a sparse patch — see {@link UpdateTopicCommand}. A null field is left alone; a
     * present {@code queries} map replaces the topic's queries wholesale.
     */
    @Transactional
    public Topic update(UUID topicId, UpdateTopicCommand command) {
        Topic topic = get(topicId);

        if (command.name() != null) {
            String name = normalizeName(command.name());
            requireNameAvailable(name, topicId);
            topic.setName(name);
        }
        if (command.enabled() != null) {
            topic.setEnabled(command.enabled());
        }
        if (command.queries() != null) {
            topic.replaceQueries(validateQueries(command.queries()));
        }
        return topicRepository.save(topic);
    }

    /**
     * Deletes the topic and its queries. Signals already collected for it survive with their
     * {@code topicId} nulled by the schema's {@code ON DELETE SET NULL} — the user is saying
     * "stop tracking this", not "throw away what it found". {@code enabled = false} is the
     * reversible way to say the same thing.
     */
    @Transactional
    public void delete(UUID topicId) {
        if (!topicRepository.existsById(topicId)) {
            throw new TopicNotFoundException(topicId);
        }
        topicRepository.deleteById(topicId);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String trimmed = name.strip();
        if (trimmed.length() > Topic.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be at most %d characters".formatted(Topic.MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    /**
     * @param selfId the topic being renamed, excluded from the check so that saving a topic
     *               under the name it already holds is not a conflict with itself
     */
    private void requireNameAvailable(String name, UUID selfId) {
        topicRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), selfId)) {
                throw new DuplicateTopicException(name);
            }
        });
    }

    private static Map<SignalSource, String> validateQueries(Map<SignalSource, String> queries) {
        Map<SignalSource, String> validated = new EnumMap<>(SignalSource.class);
        if (queries == null) {
            return validated;
        }
        queries.forEach((source, query) -> {
            if (source == null) {
                throw new IllegalArgumentException("query source must not be null");
            }
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException(
                        "query for %s must not be blank; omit the source instead".formatted(source));
            }
            String trimmed = query.strip();
            if (trimmed.length() > Topic.MAX_QUERY_LENGTH) {
                throw new IllegalArgumentException("query for %s must be at most %d characters"
                        .formatted(source, Topic.MAX_QUERY_LENGTH));
            }
            validated.put(source, trimmed);
        });
        return validated;
    }
}
