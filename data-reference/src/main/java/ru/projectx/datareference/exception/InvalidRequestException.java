package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Запрос неверен по смыслу, а не по форме: строка поиска короче допустимой, предел
 * выдачи больше разрешённого, ключи переданы не тем набором. Наружу уходит как
 * {@code 400 VALIDATION_ERROR} с полем и причиной, как и отказы по форме.
 *
 * <p>В причине — правило, а не значение: значение параметра поиска может быть фамилией,
 * а она в тексте ошибки и в журнале не нужна.
 */
public class InvalidRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidRequestException(String field, String reason) {
        super(reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
