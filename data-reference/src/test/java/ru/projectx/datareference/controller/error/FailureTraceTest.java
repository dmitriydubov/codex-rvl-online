package ru.projectx.datareference.controller.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaSystemException;

class FailureTraceTest {

    private static final String FOREIGN_TEXT = "Иванов Иван 1980-05-17";

    @Test
    void namesEveryCauseWithItsOrigin_withoutTexts() {
        SQLException root = new SQLException(FOREIGN_TEXT, "23505");
        root.setStackTrace(new StackTraceElement[] {frame("org.postgresql.Driver", "execute", 42)});
        JpaSystemException wrapper = new JpaSystemException(new IllegalStateException(FOREIGN_TEXT, root));
        wrapper.setStackTrace(new StackTraceElement[] {frame("ru.example.Service", "read", 7)});
        wrapper.getCause().setStackTrace(new StackTraceElement[] {frame("ru.example.Repository", "load", 13)});

        String trace = FailureTrace.of(wrapper);

        assertThat(trace).isEqualTo("org.springframework.orm.jpa.JpaSystemException at ru.example.Service#read:7"
                + " <- java.lang.IllegalStateException at ru.example.Repository#load:13"
                + " <- java.sql.SQLException at org.postgresql.Driver#execute:42");
        assertThat(trace).doesNotContain(FOREIGN_TEXT);
    }

    @Test
    void causeWithoutStackTrace_hasUnknownOrigin() {
        IllegalStateException failure = new IllegalStateException(FOREIGN_TEXT);
        failure.setStackTrace(new StackTraceElement[0]);

        assertThat(FailureTrace.of(failure)).isEqualTo("java.lang.IllegalStateException at место неизвестно");
    }

    @Test
    void cyclicCauses_areCutAfterTenEntries() {
        IllegalStateException first = new IllegalStateException(FOREIGN_TEXT);
        IllegalArgumentException second = new IllegalArgumentException(FOREIGN_TEXT, first);
        first.initCause(second);

        assertThat(FailureTrace.of(first).split(" <- ")).hasSize(10);
    }

    private static StackTraceElement frame(String className, String method, int line) {
        return new StackTraceElement(className, method, className + ".java", line);
    }
}
