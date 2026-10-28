package ru.projectx.datareference.exception;

import java.io.Serial;

public class RestOverloadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RestOverloadException(int limit) {
        super("превышен предел одновременно обрабатываемых запросов: " + limit);
    }
}
