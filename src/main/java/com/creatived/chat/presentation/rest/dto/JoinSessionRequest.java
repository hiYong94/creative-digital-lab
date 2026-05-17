package com.creatived.chat.presentation.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSessionRequest(
        @Schema(description = "세션에 참여하는 사용자 식별자")
        @NotBlank @Size(max = 64)
        String userId
) {
}
