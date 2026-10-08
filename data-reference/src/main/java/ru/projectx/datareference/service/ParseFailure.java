package ru.projectx.datareference.service;

/**
 * Неудачная попытка разобрать сообщение 1С:УСК, как её записывают в строку сырья и в журнал. Причина состоит только
 * из своего текста сервиса, без текста чужого исключения: ошибка базы печатает значения строки, а в полисах есть
 * персональные данные.
 */
public sealed interface ParseFailure {

    String reason();

    String type();

    /** Слово «нет», если отказ пришёл не из базы. */
    String sqlState();

    /** Тот же разбор через некоторое время может пройти. */
    record Temporary(String reason, String type, String sqlState) implements ParseFailure {
    }

    /** Повтор даст тот же отказ. */
    record Permanent(String reason, String type, String sqlState) implements ParseFailure {
    }
}
