package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Конверт сообщения 1С нечитаем или в нём нет {@code metadata.message-id}. Без этого ключа сообщение нельзя
 * даже принять, потому что он нужен для дедупликации и служит ключом строки сырья. Повтор бессмыслен:
 * источник пришлёт то же самое.
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
