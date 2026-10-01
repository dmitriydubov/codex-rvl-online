package ru.projectx.datareference.service;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Предел выдачи подсказки (R-04, R-07): не задан — умолчание из конфигурации, задан — от 1
 * до максимума. Больше максимума не обрезается молча, а отклоняется: вызывающий иначе считал бы,
 * что получил всё, о чём просил.
 */
final class ResultLimits {

    private static final String FIELD = "limit";

    private ResultLimits() {
    }

    static int resolve(@Nullable Integer requested, int defaultLimit, int maxLimit) {
        int limit = Optional.ofNullable(requested).orElse(defaultLimit);
        if (limit < 1 || limit > maxLimit) {
            throw new InvalidRequestException(FIELD, "от 1 до " + maxLimit);
        }
        return limit;
    }
}
