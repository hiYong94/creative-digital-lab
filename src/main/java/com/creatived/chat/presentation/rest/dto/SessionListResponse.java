package com.creatived.chat.presentation.rest.dto;

import com.creatived.chat.domain.session.Session;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SessionListResponse(
        @Schema(description = "세션 목록") List<SessionSummary> sessions,
        @Schema(description = "현재 페이지") int page,
        @Schema(description = "페이지 크기") int size
) {
    public static SessionListResponse from(List<Session> sessions, int page, int size) {
        return new SessionListResponse(
                sessions.stream().map(SessionSummary::from).toList(),
                page,
                size
        );
    }

    public record SessionSummary(
            UUID id,
            String status,
            LocalDateTime createdAt,
            int participantCount
    ) {
        public static SessionSummary from(Session session) {
            return new SessionSummary(
                    session.getId().value(),
                    session.getStatus().name(),
                    session.getCreatedAt(),
                    (int) session.getParticipants().stream().filter(p -> !p.hasLeft()).count()
            );
        }
    }
}
