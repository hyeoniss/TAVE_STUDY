package Tave_week2.assignment.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductCreateRequest(
        @NotBlank(message = "상품 이름은 필수입니다.")
        String name,

        @Positive(message = "상품 가격은 0보다 커야 합니다.")
        int price,

        @PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
        int stockQuantity
) {
}
