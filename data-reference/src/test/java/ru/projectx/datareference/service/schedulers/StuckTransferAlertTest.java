package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.support.ImportSettings;

/**
 * Проверка зависших многочастных передач прайсов. Её единственный наблюдаемый результат — строка WARN
 * в журнале: строки сырья она не трогает. Поэтому журнал и проверяется, целиком, вместе с текстом: по этому
 * тексту зависшую передачу ищут глазами. В строке — идентификатор передачи, сколько она ждёт и сколько её
 * частей уже пришло; тел сообщений в ней нет.
 */
@ExtendWith(MockitoExtension.class)
class StuckTransferAlertTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final Duration THRESHOLD = Duration.ofMinutes(90);
    private static final int REPORT_LIMIT = 3;
    private static final UUID FIRST_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000001");
    private static final UUID SECOND_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000002");
    private static final UUID THIRD_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000003");

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
     * Строка на все передачи, а не на каждую: у одной незавершённой передачи частей может быть десятки, и
     * строка на каждую сделала бы журнал нечитаемым ровно тогда, когда его читают.
     */
    @Test
    void stuckTransfers_areReportedWithAgeAndPartsInOneWarning() {
        when(agedMessages.transfersBufferedLongerThan(THRESHOLD, REPORT_LIMIT)).thenReturn(List.of(
                transfer(FIRST_TRANSFER, Duration.ofHours(5), 12), transfer(SECOND_TRANSFER, Duration.ofHours(2), 1)));

        alert().reportStuckTransfers();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("передачи прайсов не собрались за PT1H30M: "
                    + FIRST_TRANSFER + " (ждёт PT5H, частей 12), " + SECOND_TRANSFER + " (ждёт PT2H, частей 1). "
                    + "Прайс по их услугам остался прежним; недостающие сообщения может прислать только источник");
        });
    }

    /** Достигнут предел — строка говорит, что названы не все: иначе предел молча прятал бы остальные. */
    @Test
    void reportLimitReached_warningSaysOthersMayExist() {
        when(agedMessages.transfersBufferedLongerThan(THRESHOLD, REPORT_LIMIT)).thenReturn(List.of(
                transfer(FIRST_TRANSFER, Duration.ofHours(5), 2), transfer(SECOND_TRANSFER, Duration.ofHours(4), 2),
                transfer(THIRD_TRANSFER, Duration.ofHours(3), 2)));

        alert().reportStuckTransfers();

        assertThat(logged.list).singleElement().extracting(ILoggingEvent::getFormattedMessage).asString()
                .endsWith("только источник. Названы 3 самых давних, зависших может быть больше");
    }

    /** Зависших передач нет — молчание: пустой WARN по расписанию приучил бы его не читать. */
    @Test
    void noStuckTransfers_nothingIsLogged() {
        when(agedMessages.transfersBufferedLongerThan(THRESHOLD, REPORT_LIMIT)).thenReturn(List.of());

        alert().reportStuckTransfers();

        assertThat(logged.list).isEmpty();
    }

    private StuckTransferAlert alert() {
        return new StuckTransferAlert(agedMessages,
                ImportSettings.defaults().stuckTransferThreshold(THRESHOLD).stuckTransferReportLimit(REPORT_LIMIT)
                        .build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BufferedTransfer transfer(UUID correlationId, Duration waiting, long parts) {
        return new BufferedTransfer() {

            @Override
            public UUID getCorrelationId() {
                return correlationId;
            }

            @Override
            public Instant getFirstPartReceivedAt() {
                return NOW.minus(waiting);
            }

            @Override
            public long getParts() {
                return parts;
            }
        };
    }

    private static Logger alertLog() {
        return (Logger) LoggerFactory.getLogger(StuckTransferAlert.class);
    }
}
