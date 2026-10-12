package ru.projectx.datareference.controller;

import org.springframework.core.Ordered;

final class ErrorAdviceOrder {

    static final int REFERENCE = 1;
    static final int REQUEST = 2;
    static final int SERVER_FAILURE = Ordered.LOWEST_PRECEDENCE;

    private ErrorAdviceOrder() {
    }
}
