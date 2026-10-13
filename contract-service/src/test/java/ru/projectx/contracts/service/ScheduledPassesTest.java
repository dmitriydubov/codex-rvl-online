package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ScheduledPassesTest {

    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(ScheduledPassesTest.class);

    @BeforeEach
    void collectJournal() {
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void pass_runsOnceAndLogsNothingWhenItSucceeds() {
        AtomicInteger runs = new AtomicInteger();

        ScheduledPasses.run(log, "проверки", runs::incrementAndGet);

        assertThat(runs).hasValue(1);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void failedPass_isLoggedWithTypeAndSqlStateOnlyAndNothingIsThrown() {
        assertThatCode(() -> ScheduledPasses.run(log, "обработки входящих сообщений", () -> {
            throw new IllegalStateException("Тестова", new SQLException("locked", "55P03"));
        })).doesNotThrowAnyException();

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo(
                    "проход обработки входящих сообщений не выполнен: java.lang.IllegalStateException, SQLSTATE 55P03");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
