package ru.projectx.datareference.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

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
