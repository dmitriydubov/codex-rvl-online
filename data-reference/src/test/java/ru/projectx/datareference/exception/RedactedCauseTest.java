package ru.projectx.datareference.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class RedactedCauseTest {

    private static final String VALUE = "1980-05-17";

    @Test
    void cause_keepsTypeAndStackTraceButNotText() {
        var original = new DateTimeParseException("Text '" + VALUE + "' could not be parsed", VALUE, 0);

        Throwable cause = new OneCFieldException("поле birthDate не является датой", original).getCause();

        assertThat(cause).hasMessage("java.time.format.DateTimeParseException").hasNoCause();
        assertThat(cause.getStackTrace()).containsExactly(original.getStackTrace());
    }

    @Test
    void nestedCauses_areRedactedToo() {
        var original = new UncheckedIOException(VALUE, new IOException(VALUE));

        Throwable cause = new OneCFieldException("тело сообщения не читается как JSON", original).getCause();

        assertThat(cause).hasMessage("java.io.UncheckedIOException")
                .cause().hasMessage("java.io.IOException").hasNoCause();
    }
}
