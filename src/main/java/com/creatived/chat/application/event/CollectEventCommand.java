package com.creatived.chat.application.event;

import com.creatived.chat.domain.event.EventType;

import java.util.Map;
import java.util.UUID;

public record CollectEventCommand(
        UUID sessionId,
        String userId,
        String clientEventId,
        EventType type,
        Map<String, Object> payload,
        long sequenceNo
) {
}
