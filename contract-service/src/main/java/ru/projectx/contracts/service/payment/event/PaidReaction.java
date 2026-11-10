package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;

@Component
class PaidReaction implements OrderEventReaction {

    private final PaymentOutcomes outcomes;

    PaidReaction(PaymentOutcomes outcomes) {
        this.outcomes = outcomes;
    }

    @Override
    public String eventType() {
        return PaymentOrderEvents.PAID;
    }

    @Override
    public Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now) {
        Instant paidAt = event.updated()
                .orElseThrow(() -> ContractViolationException.invalidValue(PaymentOrderEvents.UPDATED));
        boolean paid = target.invoiced().filter(locked -> outcomes.paid(locked, paidAt, now)).isPresent();
        return paid ? Optional.empty() : Optional.of(Ignored.outOfOrder(eventType(), target.standing()));
    }
}
