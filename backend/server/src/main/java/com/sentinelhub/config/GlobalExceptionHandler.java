package com.sentinelhub.config;

import com.sentinelhub.common.dto.ApiResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(withRequestId(ApiResponse.error(40000, ex.getMessage(), null)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity.badRequest().body(withRequestId(ApiResponse.error(40001, msg, null)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> generic(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(withRequestId(ApiResponse.error(50000, "internal error", null)));
    }

    private static ApiResponse<Void> withRequestId(ApiResponse<Void> response) {
        return new ApiResponse<>(response.code(), response.message(), null, MDC.get(RequestIdFilter.MDC_KEY), response.details());
    }
}
