package ru.projectx.contracts.exception;

import java.util.UUID;

public class RequestCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RequestCancelledException(UUID individualRequestId) {
        super("работа по заявке " + individualRequestId + " прекращена");
    }
}
