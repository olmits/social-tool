package com.omits.social_api.draft.exception;

import java.util.UUID;

public class DraftNotFoundException extends RuntimeException {

    public DraftNotFoundException(UUID draftId) {
        super("No draft found with id " + draftId);
    }
}
