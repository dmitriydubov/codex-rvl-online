package ru.projectx.datareference.controller;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

final class CurrentRequest {

    private CurrentRequest() {
    }

    static @Nullable String id() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
