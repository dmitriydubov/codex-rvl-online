package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.payment.PaymentOrderEvents;
import ru.projectx.contracts.service.payment.PaymentAlert;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLog;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class PaidReaction implements OrderEventReaction {

    static final String UNEXPECTED = "оплата поступила по заказу, который не ждёт оплаты";
    private static final Logger log = LoggerFactory.getLogger(PaidReaction.class);

    private final PaymentOutcomes outcomes;
    private final PaymentMetrics metrics;

    PaidReaction(PaymentOutcomes outcomes, PaymentMetrics metrics) {
        this.outcomes = outcomes;
        this.metrics = metrics;
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
        if (paid) {
            return Optional.empty();
        }
        alertUnlessPaidBefore(target);
        return Optional.of(Ignored.outOfOrder(eventType(), target.standing()));
    }

    private void alertUnlessPaidBefore(EventTarget target) {
        boolean paidBefore = target.invoice().map(OrderInvoice::view)
                .filter(invoice -> invoice.state() == InvoiceState.PAID)
                .isPresent();
        if (!paidBefore) {
            metrics.countAlert(PaymentAlert.UNEXPECTED_STATE);
            PaymentOrderLog.write(log.atError(), target.locked().contract(), UNEXPECTED);
        }
    }
}
