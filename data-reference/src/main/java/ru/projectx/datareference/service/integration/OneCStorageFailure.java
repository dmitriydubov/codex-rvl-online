package ru.projectx.datareference.service.integration;

import java.util.Optional;

/**
 * Чем кончается для сообщения 1С:УСК отказ базы при записи сырья. Решение принимается по коду SQLSTATE
 * отказа, а не по типу исключения Spring: один и тот же тип приходит и на тело, которое база не примет
 * никогда, и на оборванное соединение, а судьба сообщения у них разная.
 */
enum OneCStorageFailure {

    /**
     * Постоянный отказ: база не принимает само тело (класс 22 — например, JSON-escape нулевого символа,
     * который не умещается в {@code jsonb}). Повтор даст то же самое, сообщение уходит в карантин.
     */
    POISON,

    /**
     * Гонка двух доставок одного {@code message-id} (23505): первая уже записала отметку во входящих, вторая
     * упала на ключе. Повтор после паузы примет сообщение как дубль.
     */
    DUPLICATE_RACE,

    /**
     * Всё остальное — временный отказ хранилища: нет соединения (класс 08), не дождались блокировки
     * (55P03), оператор отменён по таймауту (57014), неизвестный код или вовсе не ошибка базы. Повторять
     * сообщение бессмысленно, пока база не вернётся, поэтому потребители встают на паузу. Неизвестный отказ
     * лучше задержать, чем потерять.
     */
    OUTAGE;

    private static final String DATA_EXCEPTION_CLASS = "22";
    private static final String UNIQUE_VIOLATION = "23505";

    /** Исход по коду SQLSTATE отказа; пустой код — отказ не из базы либо без кода. */
    static OneCStorageFailure ofSqlState(Optional<String> sqlState) {
        if (sqlState.filter(code -> code.startsWith(DATA_EXCEPTION_CLASS)).isPresent()) {
            return POISON;
        }
        if (sqlState.filter(UNIQUE_VIOLATION::equals).isPresent()) {
            return DUPLICATE_RACE;
        }
        return OUTAGE;
    }
}
