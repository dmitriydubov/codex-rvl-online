package ru.projectx.contracts.service.payment.order;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentOrderAwaiting {

    static final String OVERDUE = "contracts.km.contracts.without.order.overdue";

    private final PaymentOrderRepository orders;
    private final BackgroundLeadership leadership;
    private final Duration threshold;
    private final Clock clock;
    private final AtomicLong overdue = new AtomicLong();

    public PaymentOrderAwaiting(PaymentOrderRepository orders, BackgroundLeadership leadership,
            PaymentOrderPassesProperties passes, Clock clock, MeterRegistry registry) {
        this.orders = orders;
        this.leadership = leadership;
        this.threshold = passes.awaitingThreshold();
        this.clock = clock;
        Gauge.builder(OVERDUE, overdue, AtomicLong::get)
                .description("акцептованные договоры КМ, заказ на оплату по которым не создан дольше порога")
                .register(registry);
    }

    @Transactional(readOnly = true)
    public void refresh() {
        leadership.runByRole(this::count, () -> overdue.set(0));
    }

    private void count() {
        overdue.set(orders.countByStateAndCreatedAtBefore(PaymentOrderState.AWAITING_CREATION,
                clock.instant().minus(threshold)));
    }
}
