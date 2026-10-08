package ru.projectx.datareference.service;

import java.sql.SQLException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Достаёт SQLSTATE отказа базы из цепочки причин по одному правилу для REST и приёма из брокера. Берётся самый глубокий
 * {@link SQLException} с кодом, а не корень: при обрыве соединения драйвер PostgreSQL кладёт код класса 08 поверх
 * {@code IOException}, у которого кода нет.
 */
public final class SqlStates {

    private static final String NONE = "нет";

    private SqlStates() {
    }

    /** Для журнала и health: вместо отсутствующего кода пишется слово «нет». */
    public static String deepestOrNone(Throwable failure) {
        return deepest(failure).orElse(NONE);
    }

    /** Исключение JDBC без кода глубже в цепочке найденный код не отменяет. */
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
