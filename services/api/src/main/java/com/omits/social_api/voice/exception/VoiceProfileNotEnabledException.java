package com.omits.social_api.voice.exception;

import java.util.UUID;

/**
 * A draft was requested in a voice that exists but is switched off. Rejected rather than used,
 * for the same reason {@code TopicNotEnabledException} rejects a disabled topic: disabling is
 * the author saying "stop writing like this", and quietly honouring the request anyway would
 * produce a draft in a voice the panel presents as retired.
 */
public class VoiceProfileNotEnabledException extends RuntimeException {

    public VoiceProfileNotEnabledException(UUID voiceProfileId) {
        super("Voice profile " + voiceProfileId + " is disabled and cannot be used for drafting");
    }
}
