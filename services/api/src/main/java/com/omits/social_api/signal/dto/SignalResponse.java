package com.omits.social_api.signal.dto;

import com.omits.social_api.signal.Signal;
import com.omits.social_api.signal.model.SignalSource;

import java.time.Instant;
import java.util.UUID;

public record SignalResponse(UUID id, SignalSource source, String externalId, String topic, String url,
                             int score, String rawPayload, Instant fetchedAt, Instant createdAt) {

    public static SignalResponse from(Signal signal) {
        return new SignalResponse(signal.getId(), signal.getSource(), signal.getExternalId(),
                signal.getTopic(), signal.getUrl(), signal.getScore(), signal.getRawPayload(),
                signal.getFetchedAt(), signal.getCreatedAt());
    }
}
