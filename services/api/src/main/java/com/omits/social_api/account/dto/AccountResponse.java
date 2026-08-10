package com.omits.social_api.account.dto;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;

import java.time.Instant;
import java.util.UUID;

/**
 * @param credentialRef the account's entry in the credential store — a Secrets Manager secret
 *                      name in production. This is a <em>reference</em>, never the credential
 *                      itself: resolving it requires access to the store, which callers get
 *                      from their own IAM role. It is exposed so the Go publisher can resolve
 *                      the account's credential at publish time, as described in the root
 *                      PLAN.md; the admin panel has no use for it.
 */
public record AccountResponse(UUID id, Platform platform, String handle, String instance,
                               AccountStatus status, String credentialRef,
                               Instant createdAt, Instant updatedAt) {

    public static AccountResponse from(Account account, String instance) {
        return new AccountResponse(account.getId(), account.getPlatform(), account.getHandle(),
                instance, account.getStatus(), account.getCredentialRef(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
