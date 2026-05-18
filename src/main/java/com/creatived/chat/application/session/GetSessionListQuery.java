package com.creatived.chat.application.session;

import com.creatived.chat.domain.session.SessionStatus;

import java.time.LocalDateTime;

public record GetSessionListQuery(
        int page,
        int size,
        SessionStatus status,
        LocalDateTime from,
        LocalDateTime to
) {
}
