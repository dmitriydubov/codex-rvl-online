package ru.projectx.contracts.entity.payment;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public enum InvoiceTransition {
    DOCUMENT_CONFIRMED(EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED),
            InvoiceState.READY, InvoiceMark.NONE),
    REMINDERS_EXHAUSTED(EnumSet.of(InvoiceState.AWAITING_DOCUMENT), InvoiceState.DOCUMENT_UNCONFIRMED,
            InvoiceMark.NONE),
    DOCUMENT_RESENT(EnumSet.of(InvoiceState.AWAITING_DOCUMENT, InvoiceState.DOCUMENT_UNCONFIRMED),
            InvoiceState.AWAITING_DOCUMENT, InvoiceMark.REPEATS_RESET),
    PAYMENT_AWAITED(EnumSet.of(InvoiceState.CHECKOUT), InvoiceState.ISSUED, InvoiceMark.NONE),
    REPLY_LOST(EnumSet.of(InvoiceState.CHECKOUT), InvoiceState.ISSUED, InvoiceMark.LOST_REPLY_REPORTED),
    REPLY_LOSS_REPORTED(EnumSet.of(InvoiceState.ISSUED), InvoiceState.ISSUED, InvoiceMark.LOST_REPLY_REPORTED),
    REPLY_LOSS_REPORTED_AFTER_FAILURE(EnumSet.of(InvoiceState.PAYMENT_ERROR), InvoiceState.PAYMENT_ERROR,
            InvoiceMark.LOST_REPLY_REPORTED),
    PAYMENT_FAILED(EnumSet.of(InvoiceState.ISSUED), InvoiceState.PAYMENT_ERROR, InvoiceMark.NONE),
    PAYMENT_OVERDUE(EnumSet.of(InvoiceState.CHECKOUT, InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR),
            InvoiceState.OVERDUE, InvoiceMark.NONE);

    private final Set<InvoiceState> from;
    private final InvoiceState to;
    private final InvoiceMark mark;

    InvoiceTransition(Set<InvoiceState> from, InvoiceState to, InvoiceMark mark) {
        this.from = from;
        this.to = to;
        this.mark = mark;
    }

    Optional<InvoiceProgress> after(InvoiceProgress current) {
        if (!from.contains(current.state()) || mark.refuses(current)) {
            return Optional.empty();
        }
        return Optional.of(mark.record(
                new InvoiceProgress(to, current.fixRepeats(), current.lostReplyReported(), current.linked())));
    }
}
