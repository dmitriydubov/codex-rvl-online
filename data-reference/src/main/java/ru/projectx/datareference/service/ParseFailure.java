package ru.projectx.datareference.service;

/**
 * Неудачная попытка разобрать сообщение 1С:УСК в том виде, в каком её записывают в строку сырья и в журнал.
 *
 * <p>Временный отказ ждёт повтора, постоянный сразу завершает обработку отказом. Причина — свой текст сервиса:
 * поле без значения или тип ошибки с кодом SQLSTATE. Текста чужого исключения в ней нет, потому что ошибка базы
 * печатает значения строки, а через разбор идут полисы с персональными данными.
 */
public sealed interface ParseFailure {

    /** Причина для строки сырья. */
    String reason();

    /** Простое имя типа ошибки для журнала. */
    String type();

    /** Код SQLSTATE для журнала или слово «нет», если отказ пришёл не из базы. */
    String sqlState();

    /** Временный отказ базы: тот же разбор через некоторое время может пройти. */
    record Temporary(String reason, String type, String sqlState) implements ParseFailure {
    }

    /** Постоянная ошибка: повтор даст тот же отказ. */
    record Permanent(String reason, String type, String sqlState) implements ParseFailure {
    }
}
