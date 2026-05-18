package com.creatived.chat.presentation.websocket.dto;

public record ReconnectRequest(
        String userId,
        String clientEventId,
        long resumeFromSequenceNo
) {}
