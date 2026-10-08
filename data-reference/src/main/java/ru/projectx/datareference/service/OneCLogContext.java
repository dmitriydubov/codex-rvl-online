package ru.projectx.datareference.service;

/**
 * Ключи MDC, общие для приёма из брокера и разбора по расписанию, чтобы один поиск по журналу находил всю историю
 * сообщения 1С. Ключи кладёт и снимает тот, кто обрабатывает сообщение, и снимает только свои.
 */
public final class OneCLogContext {

    /** {@code message-id} сообщения в канонической записи GUID. */
    public static final String MESSAGE_ID = "messageId";

    /** Имя константы {@code OneCStream}. */
    public static final String STREAM = "stream";

    /** Очередь брокера; есть только при приёме. */
    public static final String QUEUE = "queue";

    private OneCLogContext() {
    }
}
