package com.omits.social_api.topic.exception;

/**
 * A topic name already in use. Its own type, rather than an {@code IllegalArgumentException},
 * so {@code GlobalExceptionHandler} answers 409 rather than 400: the panel needs to tell
 * "that name is taken" apart from "that name is not valid", and only the status distinguishes
 * them without parsing the message.
 */
public class DuplicateTopicException extends RuntimeException {

    public DuplicateTopicException(String name) {
        super("A topic named \"" + name + "\" already exists");
    }
}
