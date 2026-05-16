package com.creatived.chat.presentation.rest.dto;

import org.slf4j.MDC;

public record ErrorResponse(
        int httpStatus,
        String code,
        String message,
        String traceId
) {
    public static ErrorResponse of(int httpStatus, String code, String message) {
        return new ErrorResponse(httpStatus, code, message, MDC.get("traceId"));
    }
}
