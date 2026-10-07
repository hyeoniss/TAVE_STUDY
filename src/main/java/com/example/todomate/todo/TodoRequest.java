package com.example.todomate.todo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class TodoRequest {
    private TodoRequest() {
    }

    public record Create(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
            String title,
            @Size(max = 500, message = "메모는 500자 이하여야 합니다.")
            String memo,
            @NotNull(message = "투두 날짜는 필수입니다.")
            LocalDate todoDate
    ) {
    }

    public record Update(
            @Size(min = 1, max = 100, message = "제목은 1자 이상 100자 이하여야 합니다.")
            String title,
            @Size(max = 500, message = "메모는 500자 이하여야 합니다.")
            String memo,
            LocalDate todoDate
    ) {
    }

    public record Completion(
            @NotNull(message = "완료 여부는 필수입니다.")
            Boolean completed
    ) {
    }
}
