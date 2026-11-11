package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.OrderingStatuses;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class ReadyOrderReconciler implements StateReconciler {

    private final ReconciliationAlerts alerts;

    ReadyOrderReconciler(ReconciliationAlerts alerts) {
        this.alerts = alerts;
    }

    @Override
    public String status() {
        return OrderingStatuses.READY_TO_PAYMENT;
    }

    @Override
    public Followup reconcile(LockedInvoice locked, OrderingOrder order, Instant now) {
        locked.invoice().pass(InvoiceTransition.DOCUMENT_CONFIRMED, now);
        InvoiceView invoice = locked.invoice().view();
        if (invoice.state().awaitsCheckout()) {
            return Followup.CHECKOUT;
        }
        alerts.unexpectedState(locked.contract(), invoice, order.status());
        return Followup.NONE;
    }
}
