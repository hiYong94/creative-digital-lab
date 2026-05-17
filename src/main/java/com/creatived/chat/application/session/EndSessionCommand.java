package com.creatived.chat.application.session;

import java.util.UUID;

public record EndSessionCommand(UUID sessionId) {
}
