package com.creatived.chat.presentation.rest.dto;

import com.creatived.chat.domain.event.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CollectEventRequest(
        @Schema(description = "클라이언트 발급 이벤트 식별자. Idempotency 키로 사용")
        @NotBlank @Size(max = 64)
        String clientEventId,

        @Schema(description = "이벤트 발생 사용자 식별자")
        @NotBlank @Size(max = 64)
        String userId,

        @Schema(description = "이벤트 타입 (JOIN | LEAVE | MESSAGE | DISCONNECT | RECONNECT)")
        @NotNull
        EventType type,

        @Schema(description = "이벤트 타입별 추가 데이터")
        Map<String, Object> payload,

        @Schema(description = "세션 내 이벤트 순서 번호")
        @Positive
        long sequenceNo
) {
}
