package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Instant;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;

final class OutcomeReconciler implements StateReconciler {

    private final String status;
    private final String eventType;
    private final Outcome outcome;
    private final ReconciliationAlerts alerts;

    OutcomeReconciler(String status, String eventType, Outcome outcome, ReconciliationAlerts alerts) {
        this.status = status;
        this.eventType = eventType;
        this.outcome = outcome;
        this.alerts = alerts;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public Followup reconcile(LockedInvoice locked, OrderingOrder order, Instant now) {
        Instant moment = order.lastMoment(eventType).orElse(now);
        if (!outcome.apply(locked, moment, now)) {
            alerts.unexpectedState(locked.contract(), locked.invoice().view(), order.status());
        }
        return Followup.NONE;
    }

    @FunctionalInterface
    interface Outcome {

        boolean apply(LockedInvoice locked, Instant moment, Instant now);
    }
}
