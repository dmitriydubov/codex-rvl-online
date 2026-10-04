package ru.projectx.datareference.service;

import java.sql.SQLException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Код SQLSTATE отказа базы данных, спрятанный в цепочке причин исключения.
 *
 * <p>Код говорит, что именно случилось в базе: 55P03 — не дождались блокировки, 57014 — оператор отменён
 * по таймауту, класс 08 — проблема с соединением, класс 22 — значение, которое база не принимает. Берётся
 * код самого глубокого {@link SQLException} в цепочке, у которого код есть, а не корневой причины: при обрыве
 * соединения драйвер PostgreSQL кладёт код класса 08 поверх {@code IOException}, и у корня кода нет; исключение
 * JDBC без кода глубже в цепочке код не отменяет.
 *
 * <p>Правило одно на весь сервис: по нему решают и REST-чтения (отвечать ли 503), и приём из брокера
 * 1С:УСК (карантин, пауза потребителей или повтор).
 */
public final class SqlStates {

    /** Что писать вместо кода в журнале и health, когда отказ пришёл не из базы либо без кода. */
    private static final String NONE = "нет";

    private SqlStates() {
    }

    /** Код SQLSTATE для журнала и health: сам код либо слово «нет», если его в цепочке причин не оказалось. */
    public static String deepestOrNone(Throwable failure) {
        return deepest(failure).orElse(NONE);
    }

    /**
     * SQLSTATE самого глубокого {@link SQLException} с кодом в цепочке причин.
     *
     * @return код или пусто, если в цепочке нет ошибки JDBC с кодом
     */
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
