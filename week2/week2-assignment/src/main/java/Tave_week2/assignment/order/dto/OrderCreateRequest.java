package Tave_week2.assignment.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderCreateRequest(
        @NotNull(message = "회원 ID는 필수입니다.")
        Long memberId,

        @NotEmpty(message = "주문 상품은 한 개 이상이어야 합니다.")
        List<@Valid OrderProductRequest> products
) {
    public record OrderProductRequest(
            @NotNull(message = "상품 ID는 필수입니다.")
            Long productId,

            @Positive(message = "주문 수량은 0보다 커야 합니다.")
            int quantity
    ) {
    }
}
