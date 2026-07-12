package com.omits.social_api.account.mastodon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mastodon_account_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MastodonAccountDetails {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Setter
    @Column(nullable = false)
    private String instance;

    public MastodonAccountDetails(UUID accountId, String instance) {
        this.accountId = accountId;
        this.instance = instance;
    }
}
