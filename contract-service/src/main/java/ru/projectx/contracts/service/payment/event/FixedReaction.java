package ru.projectx.contracts.service.payment.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class FixedReaction implements OrderEventReaction {

    @Override
    public String eventType() {
        return PaymentOrderEvents.FIXED;
    }

    @Override
    public Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now) {
        boolean confirmed = target.locked().order().view().state() == PaymentOrderState.CREATED
                && target.invoice().filter(invoice -> invoice.pass(InvoiceTransition.DOCUMENT_CONFIRMED, now)).isPresent();
        return confirmed ? Optional.empty() : Optional.of(Ignored.outOfOrder(eventType(), target.standing()));
    }
}
