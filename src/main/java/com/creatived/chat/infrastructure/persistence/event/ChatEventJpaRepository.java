package com.creatived.chat.infrastructure.persistence.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatEventJpaRepository extends JpaRepository<ChatEventJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO chat_events
                (id, session_id, user_id, client_event_id, type, payload, sequence_no, server_received_at)
            VALUES
                (:id, :sessionId, :userId, :clientEventId, :type, :payload, :sequenceNo, :serverReceivedAt)
            """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("sessionId") UUID sessionId,
            @Param("userId") String userId,
            @Param("clientEventId") String clientEventId,
            @Param("type") String type,
            @Param("payload") String payload,
            @Param("sequenceNo") long sequenceNo,
            @Param("serverReceivedAt") LocalDateTime serverReceivedAt
    );

    Optional<ChatEventJpaEntity> findBySessionIdAndClientEventId(UUID sessionId, String clientEventId);

    List<ChatEventJpaEntity> findBySessionIdOrderByServerReceivedAtAscSequenceNoAsc(UUID sessionId);

    List<ChatEventJpaEntity> findBySessionIdAndServerReceivedAtLessThanEqualOrderByServerReceivedAtAscSequenceNoAsc(
            UUID sessionId, LocalDateTime at);

    List<ChatEventJpaEntity> findBySessionIdAndSequenceNoGreaterThanOrderByServerReceivedAtAscSequenceNoAsc(
            UUID sessionId, long sequenceNo);

    long countBySessionId(UUID sessionId);
}
