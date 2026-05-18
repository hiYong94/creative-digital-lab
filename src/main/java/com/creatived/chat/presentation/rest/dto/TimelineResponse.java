package com.creatived.chat.presentation.rest.dto;

import com.creatived.chat.domain.snapshot.MessageView;
import com.creatived.chat.domain.snapshot.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TimelineResponse(
        @Schema(description = "세션 식별자") UUID sessionId,
        @Schema(description = "복원 시점 기준 활성 참여자 목록") Set<String> participants,
        @Schema(description = "복원 시점 기준 메시지 목록") List<MessageSummary> messages,
        @Schema(description = "마지막 적용된 sequenceNo") long lastSequenceNo,
        @Schema(description = "복원 전략 (FULL_REPLAY | SNAPSHOT_PLUS_REPLAY)") String restoredFrom,
        @Schema(description = "복원 기준 시각") LocalDateTime restoredAt
) {
    public record MessageSummary(String eventId, String userId, String content, LocalDateTime sentAt) {
        public static MessageSummary from(MessageView view) {
            return new MessageSummary(view.eventId(), view.userId(), view.content(), view.sentAt());
        }
    }

    public static TimelineResponse of(UUID sessionId, SessionState state, String restoredFrom, LocalDateTime at) {
        return new TimelineResponse(
                sessionId,
                state.getParticipants(),
                state.getMessages().stream().map(MessageSummary::from).toList(),
                state.getLastSequenceNo(),
                restoredFrom,
                at
        );
    }
}
