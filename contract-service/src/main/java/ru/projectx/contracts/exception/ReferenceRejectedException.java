package ru.projectx.contracts.exception;

public class ReferenceRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public ReferenceRejectedException(int status, String code) {
        super("справочник отклонил запрос: " + status + " " + code);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
