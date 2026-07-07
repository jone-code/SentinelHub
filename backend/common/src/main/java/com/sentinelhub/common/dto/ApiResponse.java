package com.sentinelhub.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified API response envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        String requestId,
        Object details
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message, Object details) {
        return new ApiResponse<>(code, message, null, null, details);
    }
}
