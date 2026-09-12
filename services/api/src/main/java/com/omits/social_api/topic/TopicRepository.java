package com.omits.social_api.topic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    /**
     * Case-insensitive, matching the {@code lower(name)} unique index. Checking any other way
     * would let the service accept a name the database then rejects.
     */
    Optional<Topic> findByNameIgnoreCase(String name);

    /**
     * Loads topics with their query maps in one query. Without the fetch join, reading
     * {@code queries} on each result is a select per topic; with it, callers may safely read
     * the map after the transaction closes.
     */
    @Query("select t from Topic t left join fetch t.queries")
    List<Topic> findAllWithQueries();

    @Query("select t from Topic t left join fetch t.queries where t.enabled = true")
    List<Topic> findEnabledWithQueries();

    @Query("select t from Topic t left join fetch t.queries where t.id = :id")
    Optional<Topic> findByIdWithQueries(@Param("id") UUID id);
}
