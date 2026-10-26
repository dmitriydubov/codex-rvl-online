package ru.projectx.contracts.service;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class SqlStates {

    public static final String NONE = "нет";

    private static final Set<String> TRANSIENT_CLASSES = Set.of("08", "40", "53");
    private static final Set<String> TRANSIENT_STATES = Set.of("55P03", "57014");

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

    public static boolean transientState(String state) {
        return TRANSIENT_STATES.contains(state) || TRANSIENT_CLASSES.stream().anyMatch(state::startsWith);
    }
}
