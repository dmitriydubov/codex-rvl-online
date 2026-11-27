package ru.projectx.contracts.exception;

public class StorageUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageUnavailableException() {
        super("хранилище справок недоступно");
    }

    public StorageUnavailableException(Throwable cause) {
        super("хранилище справок недоступно: " + cause.getClass().getSimpleName(), cause);
    }
}
