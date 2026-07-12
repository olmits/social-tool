package com.omits.social_api.account;

import com.omits.social_api.account.model.AccountStatus;
import com.omits.social_api.account.model.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByPlatformAndStatus(Platform platform, AccountStatus status);

    boolean existsByPlatformAndHandleAndStatus(Platform platform, String handle, AccountStatus status);

    @Query("select a from Account a join MastodonAccountDetails m on m.accountId = a.id "
            + "where a.handle = :handle and m.instance = :instance and a.status = :status")
    Optional<Account> findActiveMastodonAccount(@Param("handle") String handle,
                                                 @Param("instance") String instance,
                                                 @Param("status") AccountStatus status);
}
