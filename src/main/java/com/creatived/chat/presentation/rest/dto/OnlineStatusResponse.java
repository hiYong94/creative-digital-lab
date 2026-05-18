package com.creatived.chat.presentation.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnlineStatusResponse(
        @Schema(description = "사용자 ID") String userId,
        @Schema(description = "온라인 여부") boolean online
) {
    public static OnlineStatusResponse of(String userId, boolean online) {
        return new OnlineStatusResponse(userId, online);
    }
}
