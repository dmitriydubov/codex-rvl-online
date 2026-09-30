package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Конверт сообщения 1С нечитаем либо не содержит {@code metadata.message-id}.
 *
 * <p>Отличается от ошибки контракта в теле сообщения тем, что <b>сообщение нельзя даже
 * принять</b>: {@code message-id} — ключ дедупликации и ключ строки сырья, без него
 * записывать некуда. Повторять тоже бессмысленно — источник пришлёт то же самое.
 */
public class OneCEnvelopeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OneCEnvelopeException(String message) {
        super(message);
    }

    public OneCEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
