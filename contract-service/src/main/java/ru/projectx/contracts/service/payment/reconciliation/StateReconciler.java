package ru.projectx.contracts.service.payment.reconciliation;

import java.time.Instant;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;

interface StateReconciler {

    String status();

    Followup reconcile(LockedInvoice locked, OrderingOrder order, Instant now);
}
