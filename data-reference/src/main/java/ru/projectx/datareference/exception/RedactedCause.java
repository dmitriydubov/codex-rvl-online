package ru.projectx.datareference.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Копия причины отказа без её текста: имя типа и стек исходного исключения сохраняются, сообщение нет.
 * Исключения разбора JDK и Jackson цитируют исходное значение, а им бывает дата рождения из полиса, которую
 * вывел бы любой стек в журнале. Вложенные причины копируются так же.
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
