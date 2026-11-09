package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.OrderingOperation;

class OrderingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OrderingMetrics metrics = new OrderingMetrics(registry);

    @Test
    void failures_areCountedPerOperation() {
        metrics.countFailure(OrderingOperation.CREATE_ORDER);
        metrics.countFailure(OrderingOperation.CREATE_ORDER);
        metrics.countFailure(OrderingOperation.TOKEN);

        assertThat(registry.get("contracts.ordering.failures").tag("operation", "create_order").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("contracts.ordering.failures").tag("operation", "token").counter().count())
                .isEqualTo(1);
    }

    @Test
    void rejectedOrdersAndCancellations_areCountedSeparately() {
        metrics.countOrderRejected();
        metrics.countCancellationRejected();
        metrics.countCancellationRejected();

        assertThat(registry.get("contracts.payment.orders.rejected").counter().count()).isEqualTo(1);
        assertThat(registry.get("contracts.payment.order.cancel.rejected").counter().count()).isEqualTo(2);
    }

    @Test
    void operationTags_areTheOnesOfTheOperationsTable() {
        assertThat(OrderingOperation.TOKEN.tag()).isEqualTo("token");
        assertThat(OrderingOperation.CREATE_ORDER.tag()).isEqualTo("create_order");
        assertThat(OrderingOperation.FIND_ORDERS.tag()).isEqualTo("find_orders");
        assertThat(OrderingOperation.ORDER_STATE.tag()).isEqualTo("order_state");
        assertThat(OrderingOperation.CANCEL_ORDER.tag()).isEqualTo("cancel_order");
    }
}
