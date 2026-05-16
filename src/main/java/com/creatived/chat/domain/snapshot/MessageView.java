package com.creatived.chat.domain.snapshot;

import java.time.LocalDateTime;

public record MessageView(
        String eventId,
        String userId,
        String content,
        LocalDateTime sentAt
) {
}
