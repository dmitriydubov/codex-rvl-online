package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Сообщение 1С не соответствует контракту: нет обязательного поля или значение нечитаемо. Ошибка постоянная,
 * поэтому строка сырья сразу получает окончательный отказ без повторов. Текст называет поле, но не значение:
 * значением бывают персональные данные полиса.
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
