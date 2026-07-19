package com.omits.social_api.adapter.exception;

/**
 * Raised when an outbound call to a platform API fails (non-2xx response, auth failure, or an
 * empty/unexpected body). Mapped to HTTP 502 by {@code GlobalExceptionHandler}.
 */
public class PlatformApiException extends RuntimeException {

    public PlatformApiException(String message) {
        super(message);
    }
}
