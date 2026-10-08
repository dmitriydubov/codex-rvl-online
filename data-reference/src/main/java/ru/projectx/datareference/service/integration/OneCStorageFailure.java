package ru.projectx.datareference.service.integration;

import java.util.Optional;
import ru.projectx.datareference.service.SqlStates;

/**
 * Чем кончается для сообщения 1С:УСК отказ базы при записи сырья. Решение принимается по SQLSTATE, а не по типу
 * исключения Spring: один тип приходит и на отравленное тело, и на оборванное соединение. Тип запечатан, чтобы
 * компилятор следил, что разобраны все исходы.
 */
sealed interface OneCStorageFailure {

    /** Код класса ошибок данных PostgreSQL: значение, которое база не принимает. */
    String DATA_EXCEPTION_CLASS = "22";
    String UNIQUE_VIOLATION = "23505";

    /** Исход по отказу записи; код берётся у самого глубокого {@code SQLException} в цепочке причин. */
    static OneCStorageFailure of(RuntimeException failure) {
        Optional<String> sqlState = SqlStates.deepest(failure);
        if (sqlState.filter(code -> code.startsWith(DATA_EXCEPTION_CLASS)).isPresent()) {
            return new Poison(sqlState.get());
        }
        if (sqlState.filter(UNIQUE_VIOLATION::equals).isPresent()) {
            return new DuplicateRace();
        }
        return Outage.of(failure);
    }

    /**
     * Постоянный отказ: база не принимает само тело (класс 22, например нулевой символ, который не умещается в
     * {@code jsonb}). Повтор даст то же, поэтому сообщение уходит в карантин с этим кодом как причиной.
     */
    record Poison(String sqlState) implements OneCStorageFailure {
    }

    /**
     * Гонка двух доставок одного {@code message-id} (23505): первая уже записала отметку во входящих, вторая
     * упала на ключе. Повтор после паузы примет сообщение как дубль.
     */
    record DuplicateRace() implements OneCStorageFailure {
    }

    /**
     * Всё остальное — временный отказ хранилища: нет соединения, не дождались блокировки, таймаут оператора,
     * неизвестный код или вовсе не ошибка базы. Повторять до возвращения базы бессмысленно, а неизвестный отказ лучше
     * задержать, чем потерять.
     */
    record Outage(StorageOutage outage) implements OneCStorageFailure {

        /** Любой отказ как отказ хранилища: так читается и отказ самой записи в карантин. */
        static Outage of(RuntimeException failure) {
            return new Outage(new StorageOutage(SqlStates.deepestOrNone(failure), failure.getClass().getName()));
        }
    }
}
