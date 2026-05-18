package com.creatived.chat.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record GetEventsByPeriodQuery(
        UUID sessionId,
        LocalDateTime from,
        LocalDateTime to
) {
}
