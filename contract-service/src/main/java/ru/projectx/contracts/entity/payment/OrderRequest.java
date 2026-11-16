package ru.projectx.contracts.entity.payment;

public enum OrderRequest {
    CREATION(true, false),
    CANCELLATION(false, true);

    private final boolean creation;
    private final boolean cancellation;

    OrderRequest(boolean creation, boolean cancellation) {
        this.creation = creation;
        this.cancellation = cancellation;
    }

    OrderRequests after(OrderRequests sent) {
        return new OrderRequests(sent.creation() || creation, sent.cancellation() || cancellation);
    }
}
