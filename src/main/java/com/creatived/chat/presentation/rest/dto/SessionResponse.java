package com.creatived.chat.presentation.rest.dto;

import com.creatived.chat.domain.session.Session;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
        @Schema(description = "세션 식별자") UUID id,
        @Schema(description = "세션 상태 (ACTIVE | ENDED)") String status,
        @Schema(description = "세션 생성 시각") LocalDateTime createdAt,
        @Schema(description = "세션 종료 시각") LocalDateTime endedAt,
        @Schema(description = "참여자 목록") List<ParticipantResponse> participants
) {
    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId().value(),
                session.getStatus().name(),
                session.getCreatedAt(),
                session.getEndedAt(),
                session.getParticipants().stream()
                        .map(p -> new ParticipantResponse(p.getId().value(), p.getUserId(), p.getJoinedAt(), p.getLeftAt()))
                        .toList()
        );
    }

    public record ParticipantResponse(
            UUID id,
            String userId,
            LocalDateTime joinedAt,
            LocalDateTime leftAt
    ) {
    }
}
