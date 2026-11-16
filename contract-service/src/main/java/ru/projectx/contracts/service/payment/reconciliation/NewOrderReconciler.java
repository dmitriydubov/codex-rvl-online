package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.OrderingStatuses;
import ru.projectx.contracts.service.payment.order.DocumentFixes;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
class NewOrderReconciler implements StateReconciler {

    private final DocumentFixes fixes;
    private final ReconciliationAlerts alerts;
    private final int maxRepeats;

    NewOrderReconciler(DocumentFixes fixes, ReconciliationAlerts alerts, PaymentOrderPassesProperties passes) {
        this.fixes = fixes;
        this.alerts = alerts;
        this.maxRepeats = passes.reconciliation().fixRepeats();
    }

    @Override
    public String status() {
        return OrderingStatuses.NEW;
    }

    @Override
    public Followup reconcile(LockedInvoice locked, OrderingOrder order, Instant now) {
        InvoiceView invoice = locked.invoice().view();
        if (invoice.state() == InvoiceState.AWAITING_DOCUMENT) {
            remind(locked, invoice, now);
            return Followup.NONE;
        }
        if (invoice.state() != InvoiceState.DOCUMENT_UNCONFIRMED) {
            alerts.unexpectedState(locked.contract(), invoice, order.status());
        }
        return Followup.NONE;
    }

    private void remind(LockedInvoice locked, InvoiceView invoice, Instant now) {
        if (invoice.fixRepeats() < maxRepeats) {
            fixes.record(locked.contract(), locked.order(), now);
            locked.invoice().repeatFix(now);
            return;
        }
        locked.invoice().pass(InvoiceTransition.REMINDERS_EXHAUSTED, now);
        alerts.documentNotFixed(locked.contract());
    }
}
