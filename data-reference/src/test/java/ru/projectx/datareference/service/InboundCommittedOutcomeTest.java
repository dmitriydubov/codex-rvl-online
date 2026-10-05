package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Отказ, пришедший после того, как исход разбора уже зафиксирован. Так бывает, когда соединение обрывается сразу
 * после фиксации: база изменения приняла, а приложение видит обрыв и считает попытку неудачной. Записать поверх
 * зафиксированного исхода повтор или отказ значит разобрать сообщение заново или отправить применённое сообщение
 * дежурному. Поэтому исход сохраняется, а в журнал идёт предупреждение. Переход в зафиксированный исход
 * считается здесь: счётчик транзакции разбора увеличивается только после фиксации, о которой она не узнала.
 *
 * <p>Сервис возвращает статус, с которым сообщение осталось: по нему планировщик решает, держит ли сообщение свой
 * поток.
 */
@ExtendWith(MockitoExtension.class)
class InboundCommittedOutcomeTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000bb");
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final ParseFailure.Temporary CONNECTION_LOST = new ParseFailure.Temporary(
            "временный отказ базы данных: DataAccessResourceFailureException, SQLSTATE 08006",
            "DataAccessResourceFailureException", "08006");
    private static final ParseFailure.Permanent COMMIT_FAILED = new ParseFailure.Permanent(
            "непредвиденная ошибка разбора: TransactionSystemException", "TransactionSystemException", "нет");

    @Mock
    private InboundMessageRepository repository;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        serviceLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        serviceLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void temporaryFailureAfterProcessed_keepsTheOutcome() {
        InboundMessage message = existing();
        message.markProcessed(NOW);

        InboundStatus left = service().recordTemporaryFailure(MESSAGE_ID, CONNECTION_LOST);

        assertThat(left).isEqualTo(InboundStatus.PROCESSED);
        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.PROCESSED, 0, null, null, NOW));
        assertThat(counted(InboundStatus.PROCESSED)).isEqualTo(1);
        assertThat(countedTransitions()).isEqualTo(1);
    }

    /** Предупреждение называет сообщение, поток, сохранённый статус и отказ — тип и SQLSTATE, без текста. */
    @Test
    void temporaryFailureAfterCommittedOutcome_isReportedInJournal() {
        existing().markSkipped("программа не РВЛ", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, CONNECTION_LOST);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("отказ после фиксации исхода сообщения " + MESSAGE_ID
                    + " потока MKB10: исход SKIPPED сохранён, повтора не будет, отказ DataAccessResourceFailureException, "
                    + "SQLSTATE 08006");
        });
    }

    /** Часть передачи прайса, ушедшая в буфер, — тоже зафиксированный исход. */
    @Test
    void temporaryFailureAfterBuffered_keepsTheOutcome() {
        existing().markBuffered(UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc"), 2, 10);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, CONNECTION_LOST)).isEqualTo(InboundStatus.BUFFERED);
    }

    @Test
    void permanentFailureAfterProcessed_keepsTheOutcome() {
        InboundMessage message = existing();
        message.markProcessed(NOW);

        InboundStatus left = service().recordPermanentFailure(MESSAGE_ID, COMMIT_FAILED);

        assertThat(left).isEqualTo(InboundStatus.PROCESSED);
        assertThat(message.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(counted(InboundStatus.FAILED)).isZero();
        assertThat(logged.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    /** Отказ сообщения, которое ждёт повтора, записывается как обычно: исход ещё не зафиксирован. */
    @Test
    void temporaryFailureOfRetryingMessage_isRecorded() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, CONNECTION_LOST)).isEqualTo(InboundStatus.RETRY);
        assertThat(message.outcome().attempt()).isEqualTo(2);
        assertThat(logged.list).isEmpty();
    }

    @Test
    void temporaryFailureAfterLastStep_returnsFailed() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);
        message.markRetrying("вторая ошибка", NOW);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, CONNECTION_LOST)).isEqualTo(InboundStatus.FAILED);
    }

    @Test
    void permanentFailureOfNewMessage_returnsFailed() {
        existing();

        assertThat(service().recordPermanentFailure(MESSAGE_ID, COMMIT_FAILED)).isEqualTo(InboundStatus.FAILED);
    }

    /** Сколько переходов потока MKB10 в статус {@code status} посчитано. */
    private double counted(InboundStatus status) {
        return meters.get(InboundTransitionMetrics.TRANSITIONS).tag("stream", "MKB10").tag("status", status.name())
                .counter().count();
    }

    /** Сколько переходов статусов посчитано по всем потокам и статусам. */
    private double countedTransitions() {
        return meters.find(InboundTransitionMetrics.TRANSITIONS).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private InboundMessage existing() {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, NOW), "{\"data\":{}}");
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        return message;
    }

    private InboundMessageService service() {
        return new InboundMessageService(
                repository, new InboundRetryProperties(List.of(Duration.ofSeconds(30), Duration.ofMinutes(5))),
                Clock.fixed(NOW, ZoneOffset.UTC), new InboundTransitionMetrics(meters), new FinalRejectionJournal());
    }

    private static Logger serviceLog() {
        return (Logger) LoggerFactory.getLogger(InboundMessageService.class);
    }
}
