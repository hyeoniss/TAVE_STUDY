package Tave_week2.assignment.order.controller;

import Tave_week2.assignment.order.dto.OrderCreateRequest;
import Tave_week2.assignment.order.dto.OrderQueryDemoResponse;
import Tave_week2.assignment.order.dto.OrderResponse;
import Tave_week2.assignment.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderCreateRequest request) {
        return orderService.create(request);
    }

    @GetMapping
    public OrderQueryDemoResponse findAll() {
        return orderService.findAllWithNPlusOne();
    }

    @GetMapping("/n-plus-one")
    public OrderQueryDemoResponse findAllWithNPlusOne() {
        return orderService.findAllWithNPlusOne();
    }

    @GetMapping("/fetch-join")
    public OrderQueryDemoResponse findAllWithFetchJoin() {
        return orderService.findAllWithFetchJoin();
    }

    @GetMapping("/entity-graph")
    public OrderQueryDemoResponse findAllWithEntityGraph() {
        return orderService.findAllWithEntityGraph();
    }

    @GetMapping("/batch-fetch")
    public OrderQueryDemoResponse findAllWithBatchFetch() {
        return orderService.findAllWithBatchFetch();
    }
}
