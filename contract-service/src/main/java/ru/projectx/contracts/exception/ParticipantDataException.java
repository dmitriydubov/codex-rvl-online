package ru.projectx.contracts.exception;

public class ParticipantDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public ParticipantDataException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
