package ru.projectx.datareference.exception;

import java.io.Serial;

public class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidRequestException(String field, String reason) {
        super(reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
