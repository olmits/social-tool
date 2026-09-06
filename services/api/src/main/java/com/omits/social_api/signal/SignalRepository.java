package com.omits.social_api.signal;

import com.omits.social_api.signal.model.SignalSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

    /**
     * Loads the already-known signals among {@code externalIds} for one source. The ingest
     * path uses this to tell first sightings from repeats in one query per source, rather
     * than looking each item up individually.
     */
    List<Signal> findBySourceAndExternalIdIn(SignalSource source, Collection<String> externalIds);

    List<Signal> findAllByOrderByScoreDesc();

    List<Signal> findBySourceOrderByScoreDesc(SignalSource source);
}
