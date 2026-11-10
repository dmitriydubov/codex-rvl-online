package ru.projectx.contracts.service.payment;

import java.util.Locale;

public enum IgnoredEvent {
    UNKNOWN_ORDER, DOCUMENT_MISMATCH, AMOUNT_MISMATCH, FOREIGN_DOCUMENT, OUT_OF_ORDER, NOT_APPLICABLE;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
