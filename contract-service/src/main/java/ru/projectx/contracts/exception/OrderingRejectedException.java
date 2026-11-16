package ru.projectx.contracts.exception;

public class OrderingRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OrderingOperation operation;
    private final String code;

    public OrderingRejectedException(OrderingOperation operation, String code) {
        super("сервис управления заказами отклонил запрос: " + operation.tag() + ", код " + code);
        this.operation = operation;
        this.code = code;
    }

    public OrderingOperation operation() {
        return operation;
    }

    public String code() {
        return code;
    }
}
