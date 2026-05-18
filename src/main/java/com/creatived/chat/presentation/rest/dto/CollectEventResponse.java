package com.creatived.chat.presentation.rest.dto;

import com.creatived.chat.domain.event.ChatEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CollectEventResponse(
        @Schema(description = "서버 발급 이벤트 식별자") UUID eventId,
        @Schema(description = "소속 세션 식별자") UUID sessionId,
        @Schema(description = "이벤트 발생 사용자 식별자") String userId,
        @Schema(description = "클라이언트 발급 이벤트 식별자") String clientEventId,
        @Schema(description = "이벤트 타입") String type,
        @Schema(description = "이벤트 추가 데이터") Map<String, Object> payload,
        @Schema(description = "세션 내 순서 번호") long sequenceNo,
        @Schema(description = "서버 수신 시각") LocalDateTime serverReceivedAt
) {
    public static CollectEventResponse from(ChatEvent event) {
        return new CollectEventResponse(
                event.getId().value(),
                event.getSessionId().value(),
                event.getUserId(),
                event.getClientEventId().value(),
                event.getType().name(),
                event.getPayload(),
                event.getSequenceNo(),
                event.getServerReceivedAt()
        );
    }
}
