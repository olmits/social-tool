package com.omits.social_api.topic.exception;

import java.util.UUID;

public class TopicNotFoundException extends RuntimeException {

    public TopicNotFoundException(UUID topicId) {
        super("Topic " + topicId + " not found");
    }
}
