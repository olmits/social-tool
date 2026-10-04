package com.omits.social_api.voice.exception;

import java.util.UUID;

public class VoiceProfileNotFoundException extends RuntimeException {

    public VoiceProfileNotFoundException(UUID voiceProfileId) {
        super("Voice profile " + voiceProfileId + " not found");
    }
}
