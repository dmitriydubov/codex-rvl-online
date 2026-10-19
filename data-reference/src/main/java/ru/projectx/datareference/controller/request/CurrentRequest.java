package ru.projectx.datareference.controller.request;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

public final class CurrentRequest {

    private CurrentRequest() {
    }

    public static @Nullable String id() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
