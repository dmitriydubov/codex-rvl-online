package ru.projectx.contracts.service.payment;

import java.util.Locale;

public enum PaymentAlert {
    DOCUMENT_NOT_FIXED, CHECKOUT_REPLY_LOST, PAYMENT_ERROR_OVERDUE, UNEXPECTED_STATE, STATE_UNAVAILABLE,
    UNEXPECTED_CANCEL;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
