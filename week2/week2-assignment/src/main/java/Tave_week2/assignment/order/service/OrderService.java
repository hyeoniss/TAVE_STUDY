package Tave_week2.assignment.order.service;

import Tave_week2.assignment.member.domain.Member;
import Tave_week2.assignment.member.repository.MemberRepository;
import Tave_week2.assignment.order.domain.Order;
import Tave_week2.assignment.order.domain.OrderItem;
import Tave_week2.assignment.order.dto.OrderCreateRequest;
import Tave_week2.assignment.order.dto.OrderQueryDemoResponse;
import Tave_week2.assignment.order.dto.OrderResponse;
import Tave_week2.assignment.order.repository.OrderRepository;
import Tave_week2.assignment.product.domain.Product;
import Tave_week2.assignment.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    @Transactional
    public OrderResponse create(OrderCreateRequest request) {
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        Order order = new Order(member);
        request.products().forEach(itemRequest -> addOrderItem(order, itemRequest));
        return OrderResponse.from(orderRepository.save(order));
    }

    public OrderQueryDemoResponse findAllWithNPlusOne() {
        entityManager.unwrap(Session.class).setFetchBatchSize(1);
        return executeAndMeasure("N+1", orderRepository::findAll);
    }

    public OrderQueryDemoResponse findAllWithFetchJoin() {
        return executeAndMeasure("FETCH_JOIN", orderRepository::findAllWithFetchJoin);
    }

    public OrderQueryDemoResponse findAllWithEntityGraph() {
        return executeAndMeasure("ENTITY_GRAPH", orderRepository::findAllWithEntityGraph);
    }

    public OrderQueryDemoResponse findAllWithBatchFetch() {
        entityManager.unwrap(Session.class).setFetchBatchSize(100);
        return executeAndMeasure("BATCH_FETCH", orderRepository::findAll);
    }

    private void addOrderItem(Order order, OrderCreateRequest.OrderProductRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        product.decreaseStock(request.quantity());
        order.addOrderItem(new OrderItem(product, product.getPrice(), request.quantity()));
    }

    private OrderQueryDemoResponse executeAndMeasure(String strategy, Supplier<List<Order>> query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<Order> orders = query.get();
        List<OrderResponse> responses = orders.stream()
                .map(OrderResponse::from)
                .toList();

        return new OrderQueryDemoResponse(
                strategy,
                statistics.getPrepareStatementCount(),
                responses
        );
    }
}
