package com.creatived.chat.infrastructure.persistence.event;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.session.SessionId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatEventRepositoryAdapter implements ChatEventRepository {

    private final ChatEventJpaRepository chatEventJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ChatEvent save(ChatEvent event) {
        String payload = toJson(event.getPayload());
        int affected = chatEventJpaRepository.insertIgnore(
                event.getId().value(),
                event.getSessionId().value(),
                event.getUserId(),
                event.getClientEventId().value(),
                event.getType().name(),
                payload,
                event.getSequenceNo(),
                event.getServerReceivedAt()
        );

        if (affected == 0) {
            return chatEventJpaRepository
                    .findBySessionIdAndClientEventId(event.getSessionId().value(), event.getClientEventId().value())
                    .map(ChatEventJpaEntity::toDomain)
                    .orElse(event);
        }

        return chatEventJpaRepository.findById(event.getId().value())
                .map(ChatEventJpaEntity::toDomain)
                .orElse(event);
    }

    @Override
    public Optional<ChatEvent> findBySessionIdAndClientEventId(SessionId sessionId, ClientEventId clientEventId) {
        return chatEventJpaRepository
                .findBySessionIdAndClientEventId(sessionId.value(), clientEventId.value())
                .map(ChatEventJpaEntity::toDomain);
    }

    @Override
    public List<ChatEvent> findBySessionIdOrderByServerReceivedAtAscSequenceNoAsc(SessionId sessionId) {
        return chatEventJpaRepository
                .findBySessionIdOrderByServerReceivedAtAscSequenceNoAsc(sessionId.value())
                .stream().map(ChatEventJpaEntity::toDomain).toList();
    }

    @Override
    public List<ChatEvent> findBySessionIdAndServerReceivedAtBeforeOrEqual(SessionId sessionId, LocalDateTime at) {
        return chatEventJpaRepository
                .findBySessionIdAndServerReceivedAtLessThanEqualOrderByServerReceivedAtAscSequenceNoAsc(sessionId.value(), at)
                .stream().map(ChatEventJpaEntity::toDomain).toList();
    }

    @Override
    public List<ChatEvent> findBySessionIdAndSequenceNoAfter(SessionId sessionId, long sequenceNo) {
        return chatEventJpaRepository
                .findBySessionIdAndSequenceNoGreaterThanOrderByServerReceivedAtAscSequenceNoAsc(sessionId.value(), sequenceNo)
                .stream().map(ChatEventJpaEntity::toDomain).toList();
    }

    @Override
    public long countBySessionId(SessionId sessionId) {
        return chatEventJpaRepository.countBySessionId(sessionId.value());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload JSON 직렬화 실패", e);
        }
    }
}
