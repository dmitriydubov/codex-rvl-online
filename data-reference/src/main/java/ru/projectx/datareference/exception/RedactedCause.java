package ru.projectx.datareference.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Причина отказа без её текста: тип исходного исключения и место, где оно случилось, но не сообщение.
 *
 * <p>Исключения разбора JDK и Jackson повторяют в тексте исходное значение. Им бывает дата рождения или
 * фрагмент тела полиса, и любой стек в журнале вывел бы его. Копия хранит имя типа вместо текста и стек
 * исходного исключения, поэтому место отказа видно, а значение — нет. Причины исходного исключения
 * копируются так же.
 */
final class RedactedCause extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    RedactedCause(Throwable original) {
        super(original.getClass().getName(), redactedCauseOf(original));
        setStackTrace(original.getStackTrace());
    }

    private static @Nullable RedactedCause redactedCauseOf(Throwable original) {
        Throwable cause = original.getCause();
        return cause == null ? null : new RedactedCause(cause);
    }
}
