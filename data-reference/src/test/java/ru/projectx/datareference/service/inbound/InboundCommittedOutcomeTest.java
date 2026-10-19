package ru.projectx.datareference.service.inbound;

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
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundOutcome;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.ParentKind;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

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
    private static final AwaitedParent PARENT =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62"));
    private static final AwaitedParent HEAD_OFFICE =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("b515b8fc-d13c-4b0b-aae3-33e0ca86ff1c"));
    private static final InboundOutcome NEW = new InboundOutcome(InboundStatus.NEW, 0, null, null, null, null);

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

        InboundStatus left = service().recordTemporaryFailure(MESSAGE_ID, NEW, CONNECTION_LOST);

        assertThat(left).isEqualTo(InboundStatus.PROCESSED);
        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.PROCESSED, 0, null, null, NOW, null));
        assertThat(counted(InboundStatus.PROCESSED)).isEqualTo(1);
        assertThat(countedTransitions()).isEqualTo(1);
    }

    @Test
    void temporaryFailureAfterCommittedOutcome_isReportedInJournal() {
        existing().markSkipped("программа не РВЛ", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, NEW, CONNECTION_LOST);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("отказ после фиксации исхода сообщения " + MESSAGE_ID
                    + " потока MKB10: исход SKIPPED сохранён, повтора не будет, отказ DataAccessResourceFailureException, "
                    + "SQLSTATE 08006");
        });
    }

    @Test
    void temporaryFailureAfterBuffered_keepsTheOutcome() {
        existing().markBuffered(UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc"), 2, 10);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, NEW, CONNECTION_LOST))
                .isEqualTo(InboundStatus.BUFFERED);
    }

    @Test
    void permanentFailureAfterProcessed_keepsTheOutcome() {
        InboundMessage message = existing();
        message.markProcessed(NOW);

        InboundStatus left = service().recordPermanentFailure(MESSAGE_ID, NEW, COMMIT_FAILED);

        assertThat(left).isEqualTo(InboundStatus.PROCESSED);
        assertThat(message.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(counted(InboundStatus.FAILED)).isZero();
        assertThat(logged.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void temporaryFailureAfterCommittedWait_keepsTheWait() {
        InboundMessage message = existing();
        InboundOutcome before = message.outcome();
        message.markAwaitingParent(PARENT, null, NOW);
        InboundOutcome committed = message.outcome();

        InboundStatus left = service().recordTemporaryFailure(MESSAGE_ID, before, CONNECTION_LOST);

        assertThat(left).isEqualTo(InboundStatus.AWAITING_PARENT);
        assertThat(message.outcome()).isEqualTo(committed);
        assertThat(counted(InboundStatus.AWAITING_PARENT)).isEqualTo(1);
        assertThat(logged.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void permanentFailureAfterCommittedWait_keepsTheWait() {
        InboundMessage message = existing();
        InboundOutcome before = message.outcome();
        message.markAwaitingParent(PARENT, null, NOW);

        assertThat(service().recordPermanentFailure(MESSAGE_ID, before, COMMIT_FAILED))
                .isEqualTo(InboundStatus.AWAITING_PARENT);
        assertThat(message.outcome().parentWait()).isPresent();
        assertThat(counted(InboundStatus.FAILED)).isZero();
    }

    @Test
    void temporaryFailureAfterCommittedWaitForAnotherParent_keepsTheWait() {
        InboundMessage message = existing();
        message.markAwaitingParent(PARENT, null, NOW);
        InboundOutcome before = message.outcome();
        message.markAwaitingParent(HEAD_OFFICE, null, NOW);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, before, CONNECTION_LOST))
                .isEqualTo(InboundStatus.AWAITING_PARENT);
        assertThat(message.outcome().attempt()).isZero();
    }

    @Test
    void temporaryFailureOfRolledBackRecheck_schedulesARetry() {
        InboundMessage message = existing();
        message.markAwaitingParent(PARENT, null, NOW);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, message.outcome(), CONNECTION_LOST))
                .isEqualTo(InboundStatus.RETRY);
        assertThat(logged.list).isEmpty();
    }

    @Test
    void temporaryFailureOfRetryingMessage_isRecorded() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, message.outcome(), CONNECTION_LOST))
                .isEqualTo(InboundStatus.RETRY);
        assertThat(message.outcome().attempt()).isEqualTo(2);
        assertThat(logged.list).isEmpty();
    }

    @Test
    void temporaryFailureAfterLastStep_returnsFailed() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);
        message.markRetrying("вторая ошибка", NOW);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, message.outcome(), CONNECTION_LOST))
                .isEqualTo(InboundStatus.FAILED);
    }

    @Test
    void permanentFailureOfNewMessage_returnsFailed() {
        existing();

        assertThat(service().recordPermanentFailure(MESSAGE_ID, NEW, COMMIT_FAILED)).isEqualTo(InboundStatus.FAILED);
    }

    private double counted(InboundStatus status) {
        return meters.get(InboundTransitionMetrics.TRANSITIONS).tag("stream", "MKB10").tag("status", status.name())
                .counter().count();
    }

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
                Clock.fixed(NOW, ZoneOffset.UTC), new InboundTransitionMetrics(meters), new FinalRejectionJournal(), List.of());
    }

    private static Logger serviceLog() {
        return (Logger) LoggerFactory.getLogger(InboundMessageService.class);
    }
}
