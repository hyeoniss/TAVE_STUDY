package Tave_week2.assignment.global;

import Tave_week2.assignment.member.domain.Member;
import Tave_week2.assignment.member.repository.MemberRepository;
import Tave_week2.assignment.order.domain.Order;
import Tave_week2.assignment.order.domain.OrderItem;
import Tave_week2.assignment.order.repository.OrderRepository;
import Tave_week2.assignment.product.domain.Product;
import Tave_week2.assignment.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (orderRepository.count() > 0) {
            return;
        }

        List<Member> members = memberRepository.saveAll(List.of(
                new Member("민수"),
                new Member("지수"),
                new Member("현우")
        ));

        List<Product> products = productRepository.saveAll(List.of(
                new Product("키보드", 120_000, 10),
                new Product("마우스", 70_000, 10),
                new Product("모니터", 350_000, 10),
                new Product("헤드셋", 90_000, 10),
                new Product("웹캠", 80_000, 10),
                new Product("스피커", 110_000, 10)
        ));

        for (int index = 0; index < members.size(); index++) {
            Order order = new Order(members.get(index));
            Product first = products.get(index * 2);
            Product second = products.get(index * 2 + 1);
            order.addOrderItem(new OrderItem(first, first.getPrice(), 1));
            order.addOrderItem(new OrderItem(second, second.getPrice(), 2));
            orderRepository.save(order);
        }
    }
}
