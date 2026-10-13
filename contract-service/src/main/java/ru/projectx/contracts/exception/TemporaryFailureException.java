package ru.projectx.contracts.exception;

public class TemporaryFailureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TemporaryFailureException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
