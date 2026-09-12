package com.omits.social_api.signal.dto;

import com.omits.social_api.signal.Signal;
import com.omits.social_api.signal.model.SignalSource;

import java.time.Instant;
import java.util.UUID;

/**
 * @param title       the item's headline
 * @param topicId     the topic it was collected for; null for a signal polled before topics
 *                    existed, or one whose topic has since been deleted
 * @param topicName   the topic's name, resolved server-side so the panel can render a signal
 *                    row without a second request and an id-to-name join of its own. Null
 *                    exactly when {@code topicId} is
 * @param score       popularity normalized to 0-100, and what the radar ranks by
 * @param nativeScore the source's own count behind that score — points, reactions, stars.
 *                    Meaningful only next to {@code source}, since the unit differs per source
 */
public record SignalResponse(UUID id, SignalSource source, String externalId, String title,
                             UUID topicId, String topicName, String url, int score, int nativeScore,
                             String rawPayload, Instant fetchedAt, Instant createdAt) {

    public static SignalResponse from(Signal signal, String topicName) {
        return new SignalResponse(signal.getId(), signal.getSource(), signal.getExternalId(),
                signal.getTitle(), signal.getTopicId(), topicName, signal.getUrl(),
                signal.getScore(), signal.getNativeScore(), signal.getRawPayload(),
                signal.getFetchedAt(), signal.getCreatedAt());
    }
}
