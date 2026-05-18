package com.creatived.chat.application.snapshot;

import java.time.LocalDateTime;
import java.util.UUID;

public record RestoreTimelineQuery(UUID sessionId, LocalDateTime at) {
}
