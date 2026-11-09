package ru.projectx.contracts.exception;

public class OrderingOrderFailureException extends OrderingUnavailableException {

    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "сервис управления заказами не обработал заказ: ";

    public OrderingOrderFailureException(OrderingOperation operation, String reason) {
        super(MESSAGE + operation.tag() + ", " + reason, operation);
    }

    public OrderingOrderFailureException(OrderingOperation operation, String reason, Throwable cause) {
        super(MESSAGE + operation.tag() + ", " + reason, operation, cause);
    }

    @Override
    public boolean serviceWide() {
        return false;
    }
}
