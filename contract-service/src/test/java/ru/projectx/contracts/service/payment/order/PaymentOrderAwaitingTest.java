package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.support.OrderingTestProperties;

class PaymentOrderAwaitingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T09:05:00Z");

    private final PaymentOrderRepository orders = mock();
    private final BackgroundLeadership leadership = mock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentOrderAwaiting awaiting = new PaymentOrderAwaiting(orders, leadership,
            OrderingTestProperties.passes(), Clock.fixed(NOW, ZoneOffset.UTC), registry);

    @Test
    void leader_showsAwaitingOrdersOlderThanTheThreshold() {
        willAnswer(role -> {
            role.<Runnable>getArgument(0).run();
            return null;
        }).given(leadership).runByRole(any(), any());
        given(orders.countByStateAndCreatedAtBefore(PaymentOrderState.AWAITING_CREATION,
                Instant.parse("2026-02-10T08:05:00Z"))).willReturn(2L);

        awaiting.refresh();

        assertThat(overdue()).isEqualTo(2);
    }

    @Test
    void standby_showsZero() {
        willAnswer(role -> {
            role.<Runnable>getArgument(0).run();
            return null;
        }).given(leadership).runByRole(any(), any());
        given(orders.countByStateAndCreatedAtBefore(any(), any())).willReturn(3L);
        awaiting.refresh();
        willAnswer(role -> {
            role.<Runnable>getArgument(1).run();
            return null;
        }).given(leadership).runByRole(any(), any());

        awaiting.refresh();

        assertThat(overdue()).isZero();
    }

    private double overdue() {
        return registry.get("contracts.km.contracts.without.order.overdue").gauge().value();
    }
}
