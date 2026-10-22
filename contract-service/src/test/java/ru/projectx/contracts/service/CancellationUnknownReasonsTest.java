package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.support.Tasks;

class CancellationUnknownReasonsTest {

    private static final Instant CANCELLED_AT = Instant.parse("2026-02-12T11:30:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CancellationUnknownReasons reasons = new CancellationUnknownReasons(registry);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(CancellationUnknownReasons.class);

    @BeforeEach
    void startJournal() {
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void wellFormedCode_goesBeforeTheSentCommentAndIntoTheWarning() {
        Cancellation outcome = reasons.asOther(cancellation("SOMETHING_ELSE", "Стенд: комментарий"));

        assertThat(outcome).isEqualTo(new Cancellation(CancelReason.OTHER,
                "неизвестный код причины SOMETHING_ELSE; Стенд: комментарий", CANCELLED_AT));
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("прекращение работы по заявке " + Tasks.REQUEST
                    + ": неизвестный код причины SOMETHING_ELSE, прекращение применено с причиной OTHER");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void withoutSentComment_commentIsTheCodeOnly() {
        Cancellation outcome = reasons.asOther(cancellation("SOMETHING_ELSE", null));

        assertThat(outcome.comment()).isEqualTo("неизвестный код причины SOMETHING_ELSE");
    }

    @Test
    void valueOutOfCodeFormat_staysOutOfCommentAndWarning() {
        Cancellation outcome = reasons.asOther(cancellation("отказ Тестова Т. Т.", "Стенд: комментарий"));

        assertThat(outcome.comment()).isEqualTo("неизвестный код причины не по формату кода; Стенд: комментарий");
        assertThat(journal.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("прекращение работы по заявке " + Tasks.REQUEST
                        + ": код причины не по формату кода, прекращение применено с причиной OTHER")
                .doesNotContain("Тестова"));
    }

    @Test
    void everyUnknownCode_countsUnderTheCancellationEventType() {
        reasons.asOther(cancellation("SOMETHING_ELSE", null));
        reasons.asOther(cancellation("отказ", null));

        assertThat(registry.get("contracts.cancel.reason.unknown").tag("event_type", "ContractTaskCancelled")
                .counter().count()).isEqualTo(2);
        assertThat(registry.get("contracts.cancel.reason.unknown").counter().getId().getDescription())
                .isEqualTo("прекращения работы с кодом причины вне словаря, применённые с причиной OTHER");
    }

    @Test
    void counter_isRegisteredBeforeTheFirstUnknownCode() {
        assertThat(registry.get("contracts.cancel.reason.unknown").counter().count()).isZero();
    }

    private static ContractTaskCancellation cancellation(String code, @Nullable String comment) {
        return new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, code, comment);
    }
}
