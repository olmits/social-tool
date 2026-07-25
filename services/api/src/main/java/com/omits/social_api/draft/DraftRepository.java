package com.omits.social_api.draft;

import com.omits.social_api.draft.model.DraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DraftRepository extends JpaRepository<Draft, UUID> {

    List<Draft> findByAccountId(UUID accountId);

    List<Draft> findByStatus(DraftStatus status);

    List<Draft> findByAccountIdAndStatus(UUID accountId, DraftStatus status);
}
