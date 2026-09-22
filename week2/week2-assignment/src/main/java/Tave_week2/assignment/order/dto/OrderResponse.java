package Tave_week2.assignment.order.dto;

import Tave_week2.assignment.order.domain.Order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long orderId, String memberName, List<OrderItemResponse> items,
                            int totalPrice, LocalDateTime orderedAt) {
    public static OrderResponse from(Order order) {
        /*
         * orderRepository.findAll()은 Order만 먼저 조회한다.
         * Order.orderItems는 LAZY이므로 아래 getOrderItems()를 호출하는 순간
         * 주문마다 OrderItem 조회 쿼리가 한 번씩 추가로 실행된다. (1 + N)
         */
        List<OrderItemResponse> items = order.getOrderItems().stream()
                /*
                 * OrderItem.product도 LAZY이므로 각 주문상품의 Product가 초기화되지 않았다면
                 * getProduct()를 사용할 때 상품 조회 쿼리가 추가로 실행된다.
                 */
                .map(item -> new OrderItemResponse(item.getProduct().getId(), item.getProduct().getName(),
                        item.getOrderPrice(), item.getQuantity()))
                .toList();

        /*
         * Order.member 역시 LAZY이므로 getMember().getName()을 호출할 때
         * 각 주문의 Member를 조회하는 추가 쿼리가 실행된다.
         * 따라서 주문 목록을 반복해서 DTO로 변환하면 연관관계별 N개의 쿼리가 발생한다.
         */
        return new OrderResponse(order.getId(), order.getMember().getName(), items,
                order.getTotalPrice(), order.getOrderedAt());
    }

    public record OrderItemResponse(Long productId, String productName, int orderPrice, int quantity) {
    }
}
