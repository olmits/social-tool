package com.omits.social_api.adapter.bluesky;

import com.omits.social_api.adapter.exception.PlatformApiException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * Maps a non-2xx XRPC response into a {@link PlatformApiException}, carrying the failing
 * operation name, status code, and response body for diagnosis.
 */
final class BlueskyErrors {

    private BlueskyErrors() {
    }

    static Mono<Throwable> toException(String operation, ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new PlatformApiException(
                        "Bluesky " + operation + " failed (" + response.statusCode() + "): " + body));
    }
}
