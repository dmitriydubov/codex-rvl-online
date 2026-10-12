package ru.projectx.datareference.exception;

import java.io.Serial;

public class OneCEnvelopeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OneCEnvelopeException(String message) {
        super(message);
    }

    public OneCEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
