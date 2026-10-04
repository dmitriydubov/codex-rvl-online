package ru.projectx.datareference.service;

/**
 * Имена ключей MDC, по которым строка журнала находит своё сообщение 1С: и при приёме из брокера, и при
 * разборе по расписанию.
 *
 * <p>Имена общие для обоих путей, чтобы один поиск по журналу находил всю историю сообщения — от приёма
 * до исхода разбора. Класс не знает ни про брокер, ни про расписание: ключи кладёт и снимает тот, кто
 * обрабатывает сообщение, и снимает только свои.
 */
public final class OneCLogContext {

    /** Ключ MDC: {@code message-id} обрабатываемого сообщения в каноническом виде GUID. */
    public static final String MESSAGE_ID = "messageId";

    /** Ключ MDC: поток 1С, имя константы {@code OneCStream}. */
    public static final String STREAM = "stream";

    /** Ключ MDC: очередь брокера, из которой пришло сообщение. Есть только при приёме. */
    public static final String QUEUE = "queue";

    private OneCLogContext() {
    }
}
