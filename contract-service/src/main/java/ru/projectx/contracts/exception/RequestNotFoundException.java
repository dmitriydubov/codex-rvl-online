package ru.projectx.contracts.exception;

import java.util.UUID;

public class RequestNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RequestNotFoundException(UUID individualRequestId) {
        super("задачи на выпуск по заявке " + individualRequestId + " не было");
    }
}
