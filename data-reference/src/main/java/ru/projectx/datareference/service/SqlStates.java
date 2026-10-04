package ru.projectx.datareference.service;

import java.sql.SQLException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Код SQLSTATE отказа базы данных, спрятанный в цепочке причин исключения.
 *
 * <p>Код говорит, что именно случилось в базе: 55P03 — не дождались блокировки, 57014 — оператор отменён
 * по таймауту, класс 08 — проблема с соединением, класс 22 — значение, которое база не принимает. Берётся
 * код самого глубокого {@link SQLException} в цепочке, а не корневой причины: при обрыве соединения драйвер
 * PostgreSQL кладёт код класса 08 поверх {@code IOException}, и у корня кода нет.
 *
 * <p>Правило одно на весь сервис: по нему решают и REST-чтения (отвечать ли 503), и приём из брокера
 * 1С:УСК (карантин, пауза потребителей или повтор).
 */
public final class SqlStates {

    private SqlStates() {
    }

    /**
     * SQLSTATE самого глубокого {@link SQLException} в цепочке причин.
     *
     * @return код или пусто, если в цепочке нет ошибки JDBC либо у неё нет кода
     */
    public static Optional<String> deepest(Throwable failure) {
        @Nullable String found = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                found = sql.getSQLState();
            }
        }
        return Optional.ofNullable(found);
    }
}
