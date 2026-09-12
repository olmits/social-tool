package com.omits.social_api.topic.exception;

import java.util.UUID;

/**
 * A signal was ingested against a topic that exists but is switched off. Rejected rather than
 * stored, because a disabled topic is the user saying "stop collecting this" — silently
 * accepting the batch would leave the panel showing signals for a topic it presents as off.
 */
public class TopicNotEnabledException extends RuntimeException {

    public TopicNotEnabledException(UUID topicId) {
        super("Topic " + topicId + " is disabled and cannot accept new signals");
    }
}
