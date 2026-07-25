package com.omits.social_api.draft.exception;

import com.omits.social_api.draft.model.DraftStatus;

import java.util.UUID;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(UUID draftId, DraftStatus from, DraftStatus to) {
        super("Cannot transition draft " + draftId + " from " + from + " to " + to);
    }
}
