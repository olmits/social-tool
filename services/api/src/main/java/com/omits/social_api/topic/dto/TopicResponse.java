package com.omits.social_api.topic.dto;

import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.Topic;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * @param queries per-source queries, keyed by source. A source absent from the map is not
 *                polled for this topic. Ordered by source name so the panel renders a stable
 *                list rather than one that reshuffles between requests.
 */
public record TopicResponse(UUID id, String name, boolean enabled,
                            Map<SignalSource, String> queries,
                            Instant createdAt, Instant updatedAt) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName(), topic.isEnabled(),
                new TreeMap<>(topic.getQueries()), topic.getCreatedAt(), topic.getUpdatedAt());
    }
}
