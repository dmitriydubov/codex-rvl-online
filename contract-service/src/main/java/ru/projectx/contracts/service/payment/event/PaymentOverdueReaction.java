package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class PaymentOverdueReaction implements OrderEventReaction {

    private final PaymentOutcomes outcomes;

    PaymentOverdueReaction(PaymentOutcomes outcomes) {
        this.outcomes = outcomes;
    }

    @Override
    public String eventType() {
        return PaymentOrderEvents.PAYMENT_OVERDUE;
    }

    @Override
    public Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now) {
        Instant moment = event.updated().orElse(now);
        boolean overdue = target.invoiced().filter(locked -> outcomes.overdue(locked, moment, now)).isPresent();
        return overdue ? Optional.empty() : Optional.of(Ignored.outOfOrder(eventType(), target.standing()));
    }
}
