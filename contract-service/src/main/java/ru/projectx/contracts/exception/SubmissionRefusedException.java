package ru.projectx.contracts.exception;

public class SubmissionRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final String errorMessage;

    public SubmissionRefusedException(String detail, String errorCode, String errorMessage) {
        super(detail);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
