package com.omits.social_api.account.dto;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, Platform platform, String handle, String instance,
                               AccountStatus status, Instant createdAt, Instant updatedAt) {

    public static AccountResponse from(Account account, String instance) {
        return new AccountResponse(account.getId(), account.getPlatform(), account.getHandle(),
                instance, account.getStatus(), account.getCreatedAt(), account.getUpdatedAt());
    }
}
