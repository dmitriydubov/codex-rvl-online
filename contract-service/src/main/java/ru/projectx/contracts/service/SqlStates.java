package ru.projectx.contracts.service;

import java.sql.SQLException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class SqlStates {

    public static final String NONE = "нет";

    private SqlStates() {
    }

    public static String deepestOrNone(Throwable failure) {
        return deepest(failure).orElse(NONE);
    }

    public static Optional<String> deepest(Throwable failure) {
        @Nullable String found = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                found = Optional.ofNullable(sql.getSQLState()).orElse(found);
            }
        }
        return Optional.ofNullable(found);
    }
}
