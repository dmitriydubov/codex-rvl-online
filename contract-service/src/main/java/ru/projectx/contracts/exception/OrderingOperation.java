package ru.projectx.contracts.exception;

public enum OrderingOperation {
    TOKEN("token"),
    CREATE_ORDER("create_order"),
    FIND_ORDERS("find_orders"),
    ORDER_STATE("order_state"),
    CANCEL_ORDER("cancel_order");

    private final String tag;

    OrderingOperation(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
