package ru.projectx.datareference.exception;

import java.io.Serial;

/**
 * Запрос неверен по смыслу, а не по форме, например строка поиска короче допустимой; наружу уходит как
 * {@code 400 VALIDATION_ERROR}. В причине пишется правило, а не значение: значением поиска бывает фамилия,
 * а ей не место в тексте ошибки и в журнале.
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
