package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Сообщение источника не соответствует контракту: нет обязательного поля либо значение
 * нечитаемо.
 *
 * <p>Ошибка <b>постоянная</b>. Повтор даст ровно то же самое, поэтому строка сырья сразу получает
 * окончательный отказ, а не ждёт повторов. Чинится либо маппером, либо уточнением контракта, но не
 * временем.
 *
 * <p>Текст называет поле, но не значение, и становится причиной отказа в строке сырья. Исключение разбора
 * JDK или Jackson, вызвавшее отказ, лежит в цепочке причин без своего текста: такие исключения повторяют
 * исходное значение, а им бывают персональные данные полиса.
 */
public class OneCFieldException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OneCFieldException(String message) {
        super(message);
    }

    /**
     * Отказ, вызванный исключением разбора. В цепочку причин идёт его копия без текста ({@link RedactedCause}):
     * тип и стек остаются, а значение, которое исключение цитирует, — нет.
     */
    public OneCFieldException(String message, Throwable cause) {
        super(message, new RedactedCause(cause));
    }
}
