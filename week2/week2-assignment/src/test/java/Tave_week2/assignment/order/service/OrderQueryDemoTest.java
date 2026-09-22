package Tave_week2.assignment.order.service;

import Tave_week2.assignment.order.dto.OrderQueryDemoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderQueryDemoTest {

    @Autowired
    private OrderService orderService;

    @Test
    void N플러스1과_세가지_해결방식의_쿼리수를_비교한다() {
        OrderQueryDemoResponse nPlusOne = orderService.findAllWithNPlusOne();
        OrderQueryDemoResponse fetchJoin = orderService.findAllWithFetchJoin();
        OrderQueryDemoResponse entityGraph = orderService.findAllWithEntityGraph();
        OrderQueryDemoResponse batchFetch = orderService.findAllWithBatchFetch();

        assertThat(nPlusOne.queryCount()).isEqualTo(13);
        assertThat(fetchJoin.queryCount()).isEqualTo(1);
        assertThat(entityGraph.queryCount()).isEqualTo(1);
        assertThat(batchFetch.queryCount()).isEqualTo(4);
    }
}
