package com.omits.social_api.signal.exception;

import java.util.UUID;

public class SignalNotFoundException extends RuntimeException {

    public SignalNotFoundException(UUID signalId) {
        super("Signal " + signalId + " not found");
    }
}
