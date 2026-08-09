package com.omits.social_api.draft.exception;

import com.omits.social_api.account.model.Platform;

import java.util.UUID;

/**
 * Raised when a draft is created for a platform its target account does not publish to —
 * a draft is always posted through its account's own adapter, so the two must agree.
 */
public class PlatformMismatchException extends RuntimeException {

    public PlatformMismatchException(UUID accountId, Platform accountPlatform, Platform draftPlatform) {
        super("Account " + accountId + " is a " + accountPlatform + " account; cannot create a "
                + draftPlatform + " draft for it");
    }
}
