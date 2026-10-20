package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundOutcome;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.database.DatabaseClockRepository;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

@ExtendWith(MockitoExtension.class)
class FailedMessageJournalTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000bb");
    private static final String QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(30));
    private static final ParseFailure.Temporary LOCK_TIMEOUT = new ParseFailure.Temporary(
            "временный отказ базы данных: CannotAcquireLockException, SQLSTATE 55P03",
            "CannotAcquireLockException", "55P03");

    @Mock
    private InboundMessageRepository repository;

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
    void permanentFailure_isReportedAsErrorWithAttemptTypeAndSqlState() {
        existing();

        service().recordPermanentFailure(MESSAGE_ID, unchanged(),
                new ParseFailure.Permanent("поле ИНН длиннее 12 символов", "OneCFieldException", "нет"));

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("сообщение " + MESSAGE_ID
                    + " потока CONTRAGENTS отклонено окончательно на попытке 1: OneCFieldException, SQLSTATE нет");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void permanentFailureAfterTemporaryOne_namesTheAttemptThatFailed() {
        existing().markRetrying(LOCK_TIMEOUT.reason(), NOW);

        service().recordPermanentFailure(MESSAGE_ID, unchanged(), new ParseFailure.Permanent(
                "база данных отклонила запись: DataIntegrityViolationException, SQLSTATE 22003",
                "DataIntegrityViolationException", "22003"));

        assertThat(logged.list).singleElement().extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("сообщение " + MESSAGE_ID + " потока CONTRAGENTS отклонено окончательно на попытке 2: "
                        + "DataIntegrityViolationException, SQLSTATE 22003");
    }

    @Test
    void exhaustedRetries_areReportedAsError() {
        existing().markRetrying(LOCK_TIMEOUT.reason(), NOW);

        service().recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("сообщение " + MESSAGE_ID
                    + " потока CONTRAGENTS отклонено окончательно на попытке 2: CannotAcquireLockException, SQLSTATE 55P03");
        });
    }

    @Test
    void temporaryFailureWithRetriesLeft_isNotAnError() {
        existing();

        service().recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT);

        assertThat(logged.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void permanentFailureInTransaction_isReportedOnlyAfterCommit() {
        existing();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service().recordPermanentFailure(MESSAGE_ID, unchanged(),
                    new ParseFailure.Permanent("поле ИНН длиннее 12 символов", "OneCFieldException", "нет"));

            assertThat(logged.list).isEmpty();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(logged.list).singleElement().extracting(ILoggingEvent::getLevel).isEqualTo(Level.ERROR);
        }
        finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void permanentFailureInRolledBackTransaction_isNotReported() {
        existing();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service().recordPermanentFailure(MESSAGE_ID, unchanged(),
                    new ParseFailure.Permanent("поле ИНН длиннее 12 символов", "OneCFieldException", "нет"));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(logged.list).isEmpty();
        }
        finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private InboundOutcome unchanged() {
        return repository.findById(MESSAGE_ID).orElseThrow().outcome();
    }

    private InboundMessage existing() {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.CONTRAGENTS, QUEUE, NOW), "{\"data\":{}}");
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        return message;
    }

    private InboundMessageService service() {
        return new InboundMessageService(repository, new InboundRetryProperties(BACKOFF),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(DatabaseClockRepository.class),
                new InboundTransitionMetrics(new SimpleMeterRegistry()),
                new FinalRejectionJournal(), List.of());
    }

    private static Logger serviceLog() {
        return (Logger) LoggerFactory.getLogger(FinalRejectionJournal.class);
    }
}
