package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Присмотр за зависшими передачами ТЗ-9. Его единственный наблюдаемый результат — строка
 * WARN в журнале: строки сырья он не трогает. Поэтому журнал и проверяется, целиком, вместе
 * с текстом: по этому тексту зависшую передачу ищут глазами.
 */
@ExtendWith(MockitoExtension.class)
class StuckTransferAlertTest {

    private static final Duration THRESHOLD = Duration.ofMinutes(90);
    private static final Instant RECEIVED_AT = Instant.parse("2026-03-14T07:00:00Z");
    private static final String QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes";
    private static final UUID FIRST_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000001");
    private static final UUID SECOND_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000002");

    @Mock
    private AgedInboundMessages agedMessages;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        alertLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        alertLog().detachAppender(logged);
        logged.stop();
    }

    /**
     * По передачам, а не по сообщениям: у одной незавершённой передачи частей может быть
     * десятки, и строка на каждую сделала бы журнал нечитаемым ровно тогда, когда его читают.
     */
    @Test
    void stuckTransfers_areReportedOnceEachInOneWarning() {
        when(agedMessages.inStatus(InboundStatus.BUFFERED, THRESHOLD)).thenReturn(List.of(
                bufferedPartOf(FIRST_TRANSFER), bufferedPartOf(FIRST_TRANSFER), bufferedPartOf(SECOND_TRANSFER)));

        alert().reportStuckTransfers();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("передачи прайсов не собрались за PT1H30M: "
                    + FIRST_TRANSFER + ", " + SECOND_TRANSFER + ". Прайс по их услугам остался прежним; "
                    + "недостающие сообщения может прислать только источник");
        });
    }

    /** Зависших передач нет — молчание: пустой WARN по расписанию приучил бы его не читать. */
    @Test
    void noStuckTransfers_nothingIsLogged() {
        when(agedMessages.inStatus(InboundStatus.BUFFERED, THRESHOLD)).thenReturn(List.of());

        alert().reportStuckTransfers();

        assertThat(logged.list).isEmpty();
    }

    private StuckTransferAlert alert() {
        return new StuckTransferAlert(agedMessages, new ImportProperties(Duration.ofSeconds(10), Duration.ofSeconds(30),
                100, THRESHOLD, Duration.ofDays(30), 500));
    }

    private static InboundMessage bufferedPartOf(UUID transfer) {
        InboundMessage part = InboundMessage.receive(UUID.randomUUID(), OneCStream.PRICE_LISTS, QUEUE, "{}", RECEIVED_AT);
        part.markBuffered(transfer, null, 1);
        return part;
    }

    private static Logger alertLog() {
        return (Logger) LoggerFactory.getLogger(StuckTransferAlert.class);
    }
}
