package Tave_week2.assignment.product.repository;

import Tave_week2.assignment.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
