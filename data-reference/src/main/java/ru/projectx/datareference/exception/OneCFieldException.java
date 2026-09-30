package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Сообщение источника не соответствует контракту: нет обязательного поля либо значение
 * нечитаемо.
 *
 * <p>Ошибка <b>постоянная</b>. Повтор даст ровно то же самое, поэтому строка сырья уходит
 * сразу в терминальный отказ, а не на лестницу повторов. Чинится либо маппером, либо
 * уточнением контракта — но не временем.
 */
public class OneCFieldException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OneCFieldException(String message) {
        super(message);
    }

    public OneCFieldException(String message, Throwable cause) {
        super(message, cause);
    }
}
