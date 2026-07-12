package com.omits.social_api.account.mastodon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MastodonAccountDetailsRepository extends JpaRepository<MastodonAccountDetails, UUID> {
}
