package ru.projectx.contracts.entity.payment;

import java.util.EnumSet;
import java.util.Set;

public enum InvoiceTransition {
    DOCUMENT_CONFIRMED(EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED),
            InvoiceState.READY),
    REMINDERS_EXHAUSTED(EnumSet.of(InvoiceState.AWAITING_DOCUMENT), InvoiceState.DOCUMENT_UNCONFIRMED),
    DOCUMENT_RESENT(EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED),
            InvoiceState.AWAITING_DOCUMENT),
    PAYMENT_AWAITED(EnumSet.of(InvoiceState.CHECKOUT), InvoiceState.ISSUED),
    REPLY_LOST(EnumSet.of(InvoiceState.CHECKOUT), InvoiceState.ISSUED),
    REPLY_LOSS_REPORTED(EnumSet.of(InvoiceState.ISSUED), InvoiceState.ISSUED),
    REPLY_LOSS_REPORTED_AFTER_FAILURE(EnumSet.of(InvoiceState.PAYMENT_ERROR), InvoiceState.PAYMENT_ERROR),
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

    int remindersAfter(int reminders) {
        return this == DOCUMENT_RESENT ? 0 : reminders;
    }

    boolean reportsLostReply() {
        return this == REPLY_LOST || this == REPLY_LOSS_REPORTED || this == REPLY_LOSS_REPORTED_AFTER_FAILURE;
    }
}
