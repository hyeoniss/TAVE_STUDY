package Tave_week2.assignment.order.repository;

import Tave_week2.assignment.order.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            select distinct o
            from Order o
            join fetch o.member
            join fetch o.orderItems oi
            join fetch oi.product
            """)
    List<Order> findAllWithFetchJoin();

    @EntityGraph(attributePaths = {"member", "orderItems", "orderItems.product"})
    @Query("select o from Order o")
    List<Order> findAllWithEntityGraph();
}
