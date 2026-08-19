package com.eventledger.gateway.repository;

import com.eventledger.gateway.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, String> {

    /**
     * Find all events for a given account, ordered by eventTimestamp ascending.
     * Fulfills requirement: "Event listings must be in chronological order by eventTimestamp."
     */
    List<EventEntity> findByAccountIdOrderByEventTimestampAsc(String accountId);
}