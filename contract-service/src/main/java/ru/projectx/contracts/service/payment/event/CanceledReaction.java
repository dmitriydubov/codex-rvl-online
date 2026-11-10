package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;

@Component
class CanceledReaction implements OrderEventReaction {

    static final String UNEXPECTED = "Ordering отменил заказ без запроса Договоров";
    static final String EVENT_TYPE = "canceled";
    private static final Logger log = LoggerFactory.getLogger(CanceledReaction.class);

    private final PaymentMetrics metrics;

    CanceledReaction(PaymentMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now) {
        if (target.locked().order().view().state().withdrawn()) {
            return Optional.of(Ignored.notApplicable(eventType()));
        }
        metrics.countAlert(PaymentAlert.UNEXPECTED_CANCEL);
        PaymentOrderLog.write(log.atError(), target.locked().contract(), UNEXPECTED);
        return Optional.empty();
    }
}
