package ru.projectx.contracts.service.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.OrderingOperation;

@Component
public class OrderingMetrics {

    static final String FAILURES = "contracts.ordering.failures";
    static final String ORDERS_REJECTED = "contracts.payment.orders.rejected";
    static final String CANCEL_REJECTED = "contracts.payment.order.cancel.rejected";
    static final String OPERATION = "operation";

    private final MeterRegistry registry;

    public OrderingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countFailure(OrderingOperation operation) {
        Counter.builder(FAILURES)
                .description("неудавшиеся вызовы сервиса управления заказами и сервера токенов по операции")
                .tag(OPERATION, operation.tag())
                .register(registry)
                .increment();
    }

    public void countOrderRejected() {
        Counter.builder(ORDERS_REJECTED)
                .description("заказы на оплату, отклонённые сервисом управления заказами или не подтверждённые")
                .register(registry)
                .increment();
    }

    public void countCancellationRejected() {
        Counter.builder(CANCEL_REJECTED)
                .description("заказы на оплату, отмену которых сервис управления заказами отклонил")
                .register(registry)
                .increment();
    }
}
