package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.OrderingStatuses;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class PendingOrderReconciler implements StateReconciler {

    private final InvoiceRecording recording;
    private final ReconciliationAlerts alerts;

    PendingOrderReconciler(InvoiceRecording recording, ReconciliationAlerts alerts) {
        this.recording = recording;
        this.alerts = alerts;
    }

    @Override
    public String status() {
        return OrderingStatuses.PENDING_PAYMENT;
    }

    @Override
    public Followup reconcile(LockedInvoice locked, OrderingOrder order, Instant now) {
        InvoiceView invoice = locked.invoice().view();
        if (invoice.state() == InvoiceState.CHECKOUT) {
            recording.replyLost(invoice.contractId());
        }
        else if (!invoice.awaitsPayment()) {
            alerts.unexpectedState(locked.contract(), invoice, order.status());
        }
        return Followup.NONE;
    }
}
