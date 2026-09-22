package Tave_week2.assignment.order.dto;

import java.util.List;

public record OrderQueryDemoResponse(
        String strategy,
        long queryCount,
        List<OrderResponse> orders
) {
}
