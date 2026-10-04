package com.omits.social_api.generation.exception;

/**
 * Raised when a draft could not be generated because the upstream call failed — a non-2xx
 * response, a timeout, a missing API key, or a response with nothing usable in it. Mapped to
 * HTTP 502 by {@code GlobalExceptionHandler}, alongside {@code PlatformApiException}: in both
 * cases this service is fine and something it depends on is not.
 */
public class DraftGenerationException extends RuntimeException {

    public DraftGenerationException(String message) {
        super(message);
    }

    public DraftGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
