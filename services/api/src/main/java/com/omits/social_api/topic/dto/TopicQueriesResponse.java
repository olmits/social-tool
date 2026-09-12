package com.omits.social_api.topic.dto;

import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.Topic;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The Go poller's work list: every enabled topic and the queries to send for it.
 *
 * <p>A projection rather than {@code TopicResponse} because the poller needs one call to
 * learn everything it must fetch this run, and none of the panel's fields (timestamps, the
 * enabled flag it has already been filtered on) mean anything to it. Keeping it separate also
 * means the panel's DTO can change shape without a worker deploy.
 */
public record TopicQueriesResponse(UUID topicId, String name, Map<SignalSource, String> queries) {

    public static TopicQueriesResponse from(Topic topic) {
        return new TopicQueriesResponse(topic.getId(), topic.getName(), new TreeMap<>(topic.getQueries()));
    }
}
