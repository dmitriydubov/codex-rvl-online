package ru.projectx.contracts.exception;

public class OrderingUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final OrderingOperation operation;

    public OrderingUnavailableException(OrderingOperation operation, String reason) {
        super("сервис управления заказами недоступен: " + operation.tag() + ", " + reason);
        this.operation = operation;
    }

    public OrderingUnavailableException(OrderingOperation operation, String reason, Throwable cause) {
        super("сервис управления заказами недоступен: " + operation.tag() + ", " + reason, cause);
        this.operation = operation;
    }

    protected OrderingUnavailableException(String message, OrderingOperation operation) {
        super(message);
        this.operation = operation;
    }

    public OrderingOperation operation() {
        return operation;
    }
}
