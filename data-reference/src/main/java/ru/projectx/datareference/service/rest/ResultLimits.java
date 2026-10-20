package ru.projectx.datareference.service.rest;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.exception.InvalidRequestException;

public final class ResultLimits {

    private static final String FIELD = "limit";

    private ResultLimits() {
    }

    public static int resolve(@Nullable Integer requested, int defaultLimit, int maxLimit) {
        int limit = Optional.ofNullable(requested).orElse(defaultLimit);
        if (limit < 1 || limit > maxLimit) {
            throw new InvalidRequestException(FIELD, "от 1 до " + maxLimit);
        }
        return limit;
    }
}
