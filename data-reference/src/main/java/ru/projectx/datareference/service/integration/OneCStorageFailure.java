package ru.projectx.datareference.service.integration;

import java.util.Optional;
import ru.projectx.datareference.service.SqlStates;

/**
 * Чем кончается для сообщения 1С:УСК отказ базы при записи сырья. Решение принимается по коду SQLSTATE
 * отказа, а не по типу исключения Spring: один и тот же тип приходит и на тело, которое база не примет
 * никогда, и на оборванное соединение, а судьба сообщения у них разная.
 *
 * <p>Исход — запечатанный тип: кто отвечает брокеру, разбирает все три случая и ни одного не пропускает,
 * за этим следит компилятор.
 */
sealed interface OneCStorageFailure {

    /** Код класса ошибок данных PostgreSQL: значение, которое база не принимает. */
    String DATA_EXCEPTION_CLASS = "22";
    String UNIQUE_VIOLATION = "23505";
    /** Что писать вместо кода, когда отказ пришёл не из базы либо без кода. */
    String NO_SQL_STATE = "нет";

    /** Исход по отказу записи; код берётся у самого глубокого {@code SQLException} в цепочке причин. */
    static OneCStorageFailure of(RuntimeException failure) {
        Optional<String> sqlState = SqlStates.deepest(failure);
        if (sqlState.filter(code -> code.startsWith(DATA_EXCEPTION_CLASS)).isPresent()) {
            return new Poison(sqlState.get());
        }
        if (sqlState.filter(UNIQUE_VIOLATION::equals).isPresent()) {
            return new DuplicateRace();
        }
        return new Outage(sqlState.orElse(NO_SQL_STATE), failure.getClass().getName());
    }

    /**
     * Постоянный отказ: база не принимает само тело (класс 22 — например, JSON-escape нулевого символа,
     * который не умещается в {@code jsonb}). Повтор даст то же самое, сообщение уходит в карантин.
     *
     * @param sqlState код отказа — он же причина карантинной записи
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
     * Всё остальное — временный отказ хранилища: нет соединения (класс 08), не дождались блокировки
     * (55P03), оператор отменён по таймауту (57014), неизвестный код или вовсе не ошибка базы. Повторять
     * сообщение бессмысленно, пока база не вернётся, поэтому потребители встают на паузу. Неизвестный отказ
     * лучше задержать, чем потерять.
     *
     * @param sqlState    код отказа или «нет»
     * @param failureType полное имя класса исключения — для журнала и health
     */
    record Outage(String sqlState, String failureType) implements OneCStorageFailure {

        StorageOutage toStorageOutage() {
            return new StorageOutage(sqlState, failureType);
        }
    }
}
