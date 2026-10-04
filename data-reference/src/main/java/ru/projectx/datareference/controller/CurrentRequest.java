package ru.projectx.datareference.controller;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Идентификатор текущего запроса для тела ошибки и записи в журнал. Его кладёт в контекст журнала
 * {@link RequestIdFilter}, поэтому вне запроса, прошедшего фильтр, идентификатора нет.
 */
final class CurrentRequest {

    private CurrentRequest() {
    }

    static @Nullable String id() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
