package com.omits.social_api.account.exception;

import com.omits.social_api.account.model.AccountStatus;

import java.util.UUID;

/**
 * Raised when an operation targets an account that exists but is no longer {@code ACTIVE}
 * (e.g. drafting for an account that has since been disconnected).
 */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(UUID accountId, AccountStatus status) {
        super("Account " + accountId + " is " + status + ", not ACTIVE");
    }
}
