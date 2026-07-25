package com.omits.social_api.draft.exception;

import java.util.UUID;

public class DisclosureRequiredException extends RuntimeException {

    public DisclosureRequiredException(UUID draftId) {
        super("Draft " + draftId + " contains affiliate links and requires disclosure before it can be approved");
    }
}
