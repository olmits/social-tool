package com.omits.social_api.account.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("No account found with id " + accountId);
    }
}
