package ru.projectx.datareference.exception;

import java.io.Serial;

public class OneCFieldException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OneCFieldException(String message) {
        super(message);
    }

    public OneCFieldException(String message, Throwable cause) {
        super(message, new RedactedCause(cause));
    }
}
