package ru.projectx.contracts.entity.payment;

import java.util.EnumSet;
import java.util.Set;

public enum InvoiceTransition {
    DOCUMENT_CONFIRMED(EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED),
            InvoiceState.READY),
    REMINDERS_EXHAUSTED(EnumSet.of(InvoiceState.AWAITING_DOCUMENT), InvoiceState.DOCUMENT_UNCONFIRMED),
    DOCUMENT_RESENT(EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED),
            InvoiceState.AWAITING_DOCUMENT),
    REPLY_LOST(EnumSet.of(InvoiceState.CHECKOUT), InvoiceState.ISSUED),
    PAYMENT_FAILED(EnumSet.of(InvoiceState.ISSUED), InvoiceState.PAYMENT_ERROR),
    PAYMENT_OVERDUE(EnumSet.of(InvoiceState.CHECKOUT, InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR),
            InvoiceState.OVERDUE);

    private final Set<InvoiceState> from;
    private final InvoiceState to;

    InvoiceTransition(Set<InvoiceState> from, InvoiceState to) {
        this.from = from;
        this.to = to;
    }

    Set<InvoiceState> from() {
        return from;
    }

    InvoiceState to() {
        return to;
    }

    boolean restartsReminders() {
        return this == DOCUMENT_RESENT;
    }
}
