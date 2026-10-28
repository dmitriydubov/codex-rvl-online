package ru.projectx.datareference.exception;

import java.io.Serial;

public class RestOverloadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int limit;

    public RestOverloadException(int limit) {
        super("превышен предел одновременно обрабатываемых запросов: " + limit);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
