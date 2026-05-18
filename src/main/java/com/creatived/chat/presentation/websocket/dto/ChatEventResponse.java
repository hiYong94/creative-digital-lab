package com.creatived.chat.presentation.websocket.dto;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.EventType;

import java.time.LocalDateTime;
import java.util.Map;

public record ChatEventResponse(
        String id,
        String sessionId,
        String userId,
        String clientEventId,
        EventType type,
        Map<String, Object> payload,
        long sequenceNo,
        LocalDateTime serverReceivedAt
) {
    public static ChatEventResponse from(ChatEvent event) {
        return new ChatEventResponse(
                event.getId().value().toString(),
                event.getSessionId().value().toString(),
                event.getUserId(),
                event.getClientEventId().value(),
                event.getType(),
                event.getPayload(),
                event.getSequenceNo(),
                event.getServerReceivedAt()
        );
    }
}
