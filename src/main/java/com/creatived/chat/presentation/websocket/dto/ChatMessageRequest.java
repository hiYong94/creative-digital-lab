package com.creatived.chat.presentation.websocket.dto;

import com.creatived.chat.domain.event.EventType;

import java.util.Map;

public record ChatMessageRequest(
        String userId,
        String clientEventId,
        EventType type,
        Map<String, Object> payload,
        long sequenceNo
) {}
