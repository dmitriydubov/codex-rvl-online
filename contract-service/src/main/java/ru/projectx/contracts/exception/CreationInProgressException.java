package ru.projectx.contracts.exception;

public class CreationInProgressException extends OrderingUnavailableException {

    private static final long serialVersionUID = 1L;

    public CreationInProgressException() {
        super("создание заказа начато другим проходом, заказ будет найден поиском", OrderingOperation.CREATE_ORDER);
    }

    @Override
    public boolean serviceWide() {
        return false;
    }
}
