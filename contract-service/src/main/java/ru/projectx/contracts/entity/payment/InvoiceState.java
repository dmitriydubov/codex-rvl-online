package ru.projectx.contracts.entity.payment;

import java.util.EnumSet;
import java.util.Set;

public enum InvoiceState {
    AWAITING_DOCUMENT,
    DOCUMENT_UNCONFIRMED,
    READY,
    CHECKOUT,
    ISSUED,
    REJECTED,
    PAYMENT_ERROR,
    PAID,
    OVERDUE;

    private static final Set<InvoiceState> AWAITING_CONFIRMATION =
            EnumSet.of(AWAITING_DOCUMENT, DOCUMENT_UNCONFIRMED);
    private static final Set<InvoiceState> AWAITING_PAYMENT = EnumSet.of(CHECKOUT, ISSUED, PAYMENT_ERROR);

    public boolean awaitsConfirmation() {
        return AWAITING_CONFIRMATION.contains(this);
    }

    public boolean awaitsCheckout() {
        return this == READY || this == CHECKOUT;
    }

    public boolean awaitsPayment() {
        return AWAITING_PAYMENT.contains(this);
    }

    public boolean invoiced() {
        return awaitsPayment() || this == PAID || this == OVERDUE;
    }

    public boolean awaitsOutcome() {
        return awaitsConfirmation() || awaitsCheckout() || awaitsPayment();
    }
}
