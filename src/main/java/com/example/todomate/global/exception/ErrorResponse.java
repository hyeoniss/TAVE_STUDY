package com.example.todomate.global.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        List<FieldError> errors,
        String path,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(false, errorCode.code(), errorCode.message(), List.of(), path, LocalDateTime.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors, String path) {
        return new ErrorResponse(false, errorCode.code(), errorCode.message(), errors, path, LocalDateTime.now());
    }

    public record FieldError(String field, Object rejectedValue, String reason) {
    }
}
