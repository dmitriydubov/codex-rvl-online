package ru.projectx.contracts.service.payment.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class PaymentErrorReaction implements OrderEventReaction {

    private final PaymentOutcomes outcomes;

    PaymentErrorReaction(PaymentOutcomes outcomes) {
        this.outcomes = outcomes;
    }

    @Override
    public String eventType() {
        return PaymentOrderEvents.PAYMENT_ERROR;
    }

    @Override
    public Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now) {
        boolean failed = target.invoiced().filter(locked -> outcomes.paymentFailed(locked, now)).isPresent();
        return failed ? Optional.empty() : Optional.of(Ignored.outOfOrder(eventType(), target.standing()));
    }
}
