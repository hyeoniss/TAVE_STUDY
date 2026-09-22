package Tave_week2.assignment.global;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp
) {
    public static ApiErrorResponse of(int status, String message) {
        return new ApiErrorResponse(status, message, LocalDateTime.now());
    }
}
