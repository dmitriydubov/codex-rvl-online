package ru.projectx.contracts.entity.payment;

public enum PaymentOrderState {
    AWAITING_CREATION,
    CREATED,
    REJECTED,
    CANCEL_PENDING,
    CANCELLED,
    CANCEL_REJECTED;

    public boolean withdrawn() {
        return this == CANCEL_PENDING || this == CANCELLED || this == CANCEL_REJECTED;
    }
}
