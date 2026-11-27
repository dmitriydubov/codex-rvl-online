package ru.projectx.contracts.exception;

public class CertificateMissingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CertificateMissingException() {
        super("объекта справки нет в хранилище");
    }

    public CertificateMissingException(Throwable cause) {
        super("объекта справки нет в хранилище", cause);
    }
}
