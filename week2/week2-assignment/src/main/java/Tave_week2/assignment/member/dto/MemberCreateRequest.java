package Tave_week2.assignment.member.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberCreateRequest(
        @NotBlank(message = "회원 이름은 필수입니다.")
        String name
) {
}
