package com.example.todomate.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(
        boolean success,
        T data,
        LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> successWithoutData() {
        return new ApiResponse<>(true, null, LocalDateTime.now());
    }
}
