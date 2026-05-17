package com.creatived.chat.application.session;

import java.util.UUID;

public record JoinSessionCommand(UUID sessionId, String userId) {
}
