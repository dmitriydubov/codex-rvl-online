package ru.projectx.datareference.service.rest;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.exception.InvalidRequestException;

public final class ResultLimits {

    public static final int MIN_LIMIT = 1;

    private static final String FIELD = "limit";

    private ResultLimits() {
    }

    public static int resolve(@Nullable Integer requested, int defaultLimit, int maxLimit) {
        int limit = Optional.ofNullable(requested).orElse(defaultLimit);
        if (limit < MIN_LIMIT || limit > maxLimit) {
            throw new InvalidRequestException(FIELD, "от " + MIN_LIMIT + " до " + maxLimit);
        }
        return limit;
    }
}
