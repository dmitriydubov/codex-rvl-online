package ru.projectx.contracts.exception;

public abstract class AcceptanceRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AcceptanceRefusal refusal;

    protected AcceptanceRefusedException(AcceptanceRefusal refusal, String message) {
        super(message);
        this.refusal = refusal;
    }

    protected AcceptanceRefusedException(AcceptanceRefusal refusal, String message, Throwable cause) {
        super(message, cause);
        this.refusal = refusal;
    }

    public AcceptanceRefusal refusal() {
        return refusal;
    }
}
