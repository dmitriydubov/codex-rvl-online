package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingAttempts;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;

@Service
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class PaymentReconciliation {

    private static final Set<PaymentOrderState> AWAITING_INVOICE =
            EnumSet.of(PaymentOrderState.CREATED, PaymentOrderState.REJECTED);
    private static final Set<InvoiceState> INVOICED = EnumSet.of(InvoiceState.CHECKOUT, InvoiceState.ISSUED,
            InvoiceState.PAYMENT_ERROR, InvoiceState.PAID, InvoiceState.OVERDUE);
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliation.class);

    private final PaymentOrderPasses passes;
    private final PaymentOrderRepository orders;
    private final Ordering ordering;
    private final OrderingAttempts attempts;
    private final ReconciliationRecording recording;
    private final InvoiceIssuing issuing;
    private final Duration threshold;
    private final Duration grace;
    private final Clock clock;

    public PaymentReconciliation(PaymentOrderPasses passes, PaymentOrderRepository orders, Ordering ordering,
            OrderingAttempts attempts, InvoiceIssuing issuing, ReconciliationRecording recording,
            PaymentOrderPassesProperties properties, Clock clock) {
        this.passes = passes;
        this.orders = orders;
        this.ordering = ordering;
        this.attempts = attempts;
        this.issuing = issuing;
        this.recording = recording;
        this.threshold = properties.reconciliation().threshold();
        this.grace = properties.reconciliation().paymentGrace();
        this.clock = clock;
    }

    public int reconcileDue() {
        Instant now = clock.instant();
        int closed = passes.eachOf(batch -> orders.findPastBoundary(AWAITING_INVOICE, INVOICED, now, batch),
                this::closeExpired);
        int reconciled = passes.eachOf(batch -> orders.findWithInvoiceToReconcile(now.minus(threshold),
                now.minus(grace), batch), this::reconcile);
        return closed + reconciled;
    }

    private boolean closeExpired(PaymentOrderView order) {
        recording.closeExpired(order.contractId());
        return true;
    }

    private boolean reconcile(PaymentOrderView order) {
        Optional<OrderingOrder> state;
        try {
            state = order.orderId().map(orderId -> attempts.run(
                    () -> ordering.session(order.correlationId()).order(orderId)));
        }
        catch (OrderingUnavailableException unavailable) {
            log.warn("состояние заказа на оплату договора КМ {} не получено: {}", order.contractId(),
                    unavailable.getMessage());
            recording.stateUnavailable(order.contractId());
            return !unavailable.serviceWide();
        }
        return state.map(known -> recording.apply(order.contractId(), known))
                .filter(Followup.CHECKOUT::equals)
                .map(checkout -> issuing.issue(order))
                .orElse(true);
    }
}
