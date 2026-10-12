package ru.projectx.contracts.exception;

public class ReferenceUnavailableException extends RuntimeException {

    public enum Reason {
        SERVICE_UNAVAILABLE,
        TIMEOUT,
        CONNECTION_FAILED,
        UNEXPECTED_RESPONSE,
        POLICY_NOT_RECEIVED
    }

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public ReferenceUnavailableException(Reason reason) {
        super("справочник недоступен: " + reason);
        this.reason = reason;
    }

    public ReferenceUnavailableException(Reason reason, Throwable cause) {
        super("справочник недоступен: " + reason, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
