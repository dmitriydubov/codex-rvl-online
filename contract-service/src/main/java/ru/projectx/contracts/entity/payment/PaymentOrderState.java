package ru.projectx.contracts.entity.payment;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentOrderState {
    AWAITING_CREATION,
    CREATED,
    REJECTED,
    CANCEL_PENDING,
    CANCELLED,
    CANCEL_REJECTED,
    EXPIRED;

    private static final Set<PaymentOrderState> WITHDRAWN =
            EnumSet.of(CANCEL_PENDING, CANCELLED, CANCEL_REJECTED, EXPIRED);

    public boolean withdrawn() {
        return WITHDRAWN.contains(this);
    }
}
