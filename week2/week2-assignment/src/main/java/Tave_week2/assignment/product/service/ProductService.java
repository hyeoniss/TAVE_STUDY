package Tave_week2.assignment.product.service;

import Tave_week2.assignment.product.domain.Product;
import Tave_week2.assignment.product.dto.ProductCreateRequest;
import Tave_week2.assignment.product.dto.ProductResponse;
import Tave_week2.assignment.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = new Product(request.name(), request.price(), request.stockQuantity());
        return ProductResponse.from(productRepository.save(product));
    }
}
