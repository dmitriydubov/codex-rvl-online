package ru.projectx.contracts.service.payment;

public final class OrderingStatuses {

    public static final String NEW = "NEW";
    public static final String READY_TO_PAYMENT = "READY_TO_PAYMENT";
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String PAYMENT_OVERDUE = "PAYMENT_OVERDUE";
    public static final String PAYMENT_ERROR = "PAYMENT_ERROR";
    public static final String CANCELED = "CANCELED";

    private OrderingStatuses() {
    }
}
