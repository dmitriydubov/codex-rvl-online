package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTerm;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.OrderingStatuses;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class FailedPaymentReconciler implements StateReconciler {

    private final InvoiceRecording recording;
    private final PaymentOutcomes outcomes;
    private final ReconciliationAlerts alerts;
    private final Duration grace;

    FailedPaymentReconciler(InvoiceRecording recording, PaymentOutcomes outcomes, ReconciliationAlerts alerts,
            PaymentOrderPassesProperties passes) {
        this.recording = recording;
        this.outcomes = outcomes;
        this.alerts = alerts;
        this.grace = passes.reconciliation().paymentGrace();
    }

    @Override
    public String status() {
        return OrderingStatuses.PAYMENT_ERROR;
    }

    @Override
    public Followup reconcile(LockedInvoice locked, OrderingOrder order, Instant now) {
        if (locked.invoice().view().state() == InvoiceState.CHECKOUT) {
            recording.replyLost(locked.invoice().view().contractId());
        }
        outcomes.paymentFailed(locked, now);
        InvoiceView invoice = locked.invoice().view();
        if (invoice.state() != InvoiceState.PAYMENT_ERROR) {
            alerts.unexpectedState(locked.contract(), invoice, order.status());
            return Followup.NONE;
        }
        closeIfOverdue(locked, invoice, now);
        return Followup.NONE;
    }

    private void closeIfOverdue(LockedInvoice locked, InvoiceView invoice, Instant now) {
        boolean overdue = invoice.term().map(InvoiceTerm::deadline)
                .filter(deadline -> now.isAfter(deadline.plus(grace)))
                .isPresent();
        if (overdue && outcomes.overdue(locked, now, now)) {
            alerts.paymentErrorOverdue(locked.contract());
        }
    }
}
