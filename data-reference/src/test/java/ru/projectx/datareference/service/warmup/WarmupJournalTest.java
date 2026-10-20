package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WarmupJournalTest {

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final WarmupJournal journal = new WarmupJournal();

    @BeforeEach
    void collect() {
        logged.start();
        journalLog().addAppender(logged);
    }

    @AfterEach
    void release() {
        journalLog().detachAppender(logged);
    }

    @Test
    void completedWarmup_isInfoWithReadsAndDuration() {
        journal.record(WarmupOutcome.completed(9), Duration.ofMillis(1250));

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("прогрев закончен: все чтения выполнены; чтений 9, длительность 1250 мс");
        });
    }

    @Test
    void interruptedWarmup_isWarnWithItsReason() {
        journal.record(WarmupOutcome.interrupted("ответ 503", 4), Duration.ofSeconds(3));

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("прогрев прерван: ответ 503; чтений 4, длительность 3000 мс");
        });
    }

    @Test
    void warmupWithoutApiServer_isInfoThatItWasSkipped() {
        journal.record(WarmupOutcome.withoutApiServer(), Duration.ZERO);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("прогрев пропущен: сервер API не запущен; чтений 0, длительность 0 мс");
        });
    }

    private static Logger journalLog() {
        return (Logger) LoggerFactory.getLogger(WarmupJournal.class);
    }
}
