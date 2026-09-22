package Tave_week2.assignment.product.dto;

import Tave_week2.assignment.product.domain.Product;

public record ProductResponse(Long id, String name, int price, int stockQuantity) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStockQuantity());
    }
}
