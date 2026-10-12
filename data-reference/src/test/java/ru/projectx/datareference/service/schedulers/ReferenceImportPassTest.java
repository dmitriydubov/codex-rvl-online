package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.projectx.datareference.service.onec.ImportOutcome.applied;
import static ru.projectx.datareference.service.onec.ImportOutcome.awaitingParent;
import static ru.projectx.datareference.service.onec.ImportOutcome.rejected;
import static ru.projectx.datareference.service.onec.ImportOutcome.skipped;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.DatabaseOutages;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.ParseFailure;
import ru.projectx.datareference.service.ParseFailures;
import ru.projectx.datareference.service.onec.ReferenceImportService;
import ru.projectx.datareference.support.ImportSettings;

@ExtendWith(MockitoExtension.class)
class ReferenceImportPassTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final int BATCH_SIZE = 7;
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

    private static final String FOREIGN_MDC_KEY = "traceId";
    private static final String FOREIGN_MDC_VALUE = "0af7651916cd43dd8448eb211c80319c";

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private ReferenceImportService importService;

    @Mock
    private OverdueParentWaits overdueParentWaits;

    @Mock
    private InboundMessageService inboundMessageService;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        schedulerLog().addAppender(logged);
    }

    @BeforeEach
    void recordFailuresAsUsual() {
        lenient().when(inboundMessageService.recordTemporaryFailure(any(), any(), any()))
                .thenReturn(InboundStatus.RETRY);
        lenient().when(inboundMessageService.recordPermanentFailure(any(), any(), any()))
                .thenReturn(InboundStatus.FAILED);
    }

    @AfterEach
    void releaseLog() {
        schedulerLog().detachAppender(logged);
        logged.stop();
        MDC.remove(FOREIGN_MDC_KEY);
    }

    @Test
    void everyDueMessage_isImported() {
        InboundMessage first = received();
        InboundMessage second = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(first, second));
        when(importService.importMessage(first)).thenReturn(applied());
        when(importService.importMessage(second)).thenReturn(applied());

        scheduler().importPendingMessages();

        verify(importService).importMessage(first);
        verify(importService).importMessage(second);
        verifyNoInteractions(inboundMessageService);
    }

    @Test
    void appliedMessage_isReportedInJournal() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenReturn(applied());

        scheduler().importPendingMessages();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("сообщение " + message.receipt().messageId() + " потока MKB10 применено");
        });
    }

    @Test
    void skippedMessage_isReportedInJournalWithReason() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenReturn(skipped("программа не РВЛ"));

        scheduler().importPendingMessages();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("сообщение " + message.receipt().messageId()
                            + " потока MKB10 пропущено: программа не РВЛ");
        });
    }

    @Test
    void rejectedMessage_isReportedInJournalAsWarningWithReason() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenReturn(rejected("передача отклонена"));

        scheduler().importPendingMessages();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("сообщение " + message.receipt().messageId()
                            + " потока MKB10 отклонено: передача отклонена");
        });
    }

    @Test
    void messageIdAndStream_areInMdcDuringImportOnly() {
        InboundMessage message = received();
        Map<String, String> seen = new HashMap<>();
        MDC.put(FOREIGN_MDC_KEY, FOREIGN_MDC_VALUE);
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenAnswer(invocation -> {
            seen.putAll(MDC.getCopyOfContextMap());
            return applied();
        });

        scheduler().importPendingMessages();

        assertThat(seen).containsEntry(OneCLogContext.MESSAGE_ID, message.receipt().messageId().toString())
                .containsEntry(OneCLogContext.STREAM, OneCStream.MKB10.name());
        assertThat(MDC.get(OneCLogContext.MESSAGE_ID)).isNull();
        assertThat(MDC.get(OneCLogContext.STREAM)).isNull();
        assertThat(MDC.get(FOREIGN_MDC_KEY)).isEqualTo(FOREIGN_MDC_VALUE);
    }

    @Test
    void failedImport_recordsFailureUnderMessageKeysAndThenClearsThem() {
        InboundMessage message = received();
        Map<String, String> seen = new HashMap<>();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(new CannotAcquireLockException("lock")).when(importService).importMessage(message);
        doAnswer(invocation -> {
            seen.putAll(MDC.getCopyOfContextMap());
            return InboundStatus.RETRY;
        }).when(inboundMessageService).recordTemporaryFailure(eq(message.receipt().messageId()), any(), any());

        scheduler().importPendingMessages();

        assertThat(seen).containsEntry(OneCLogContext.MESSAGE_ID, message.receipt().messageId().toString());
        assertThat(logged.list).singleElement()
                .satisfies(event -> assertThat(event.getMDCPropertyMap())
                        .containsEntry(OneCLogContext.STREAM, OneCStream.MKB10.name()));
        assertThat(MDC.get(OneCLogContext.MESSAGE_ID)).isNull();
        assertThat(MDC.get(OneCLogContext.STREAM)).isNull();
    }

    @Test
    void contractViolation_isRecordedAsPermanentFailure() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(new OneCFieldException("не задано поле code")).when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordPermanentFailure(message.receipt().messageId(), message.outcome(),
                new ParseFailure.Permanent("не задано поле code", "OneCFieldException", "нет"));
        verify(inboundMessageService, never()).recordTemporaryFailure(any(), any(), any());
        assertThat(logged.list).isEmpty();
    }

    @Test
    void valueRejectedByDatabase_isRecordedAsPermanentFailureWithoutItsText() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(new DataIntegrityViolationException("параметры запроса: [значения полей]",
                new SQLException("numeric field overflow", "22003")))
                .when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordPermanentFailure(message.receipt().messageId(), message.outcome(),
                new ParseFailure.Permanent(
                        "база данных отклонила запись: DataIntegrityViolationException, SQLSTATE 22003",
                        "DataIntegrityViolationException", "22003"));
        verify(inboundMessageService, never()).recordTemporaryFailure(any(), any(), any());
    }

    @Test
    void unexpectedFailure_isRecordedAsPermanentFailureNamedByType() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(new IllegalStateException("значения полей")).when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordPermanentFailure(message.receipt().messageId(), message.outcome(),
                new ParseFailure.Permanent("непредвиденная ошибка разбора: IllegalStateException",
                        "IllegalStateException", "нет"));
    }

    @Test
    void failedImport_reportsTheOutcomeReadBeforeTheAttempt() {
        InboundMessage message = received();
        InboundOutcome beforeAttempt = message.outcome();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doAnswer(invocation -> {
            message.markAwaitingParent(new AwaitedParent(ParentKind.CONTRAGENT, UUID.randomUUID()), null, NOW);
            throw new CannotAcquireLockException("lock");
        }).when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordTemporaryFailure(eq(message.receipt().messageId()),
                eq(beforeAttempt), any());
    }

    @Test
    void databaseOutage_isRecordedAsTemporaryWithoutItsText() {
        InboundMessage message = received();
        CannotAcquireLockException failure = new CannotAcquireLockException("параметры запроса: [значения полей]",
                new SQLException("lock", "55P03"));
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(failure).when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordTemporaryFailure(message.receipt().messageId(), message.outcome(),
                new ParseFailure.Temporary("временный отказ базы данных: CannotAcquireLockException, SQLSTATE 55P03",
                        "CannotAcquireLockException", "55P03"));
        verify(inboundMessageService, never()).recordPermanentFailure(any(), any(), any());
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("временная ошибка разбора сообщения "
                    + message.receipt().messageId() + " потока MKB10: CannotAcquireLockException, SQLSTATE 55P03");
        });
    }

    @Test
    void messageAwaitingItsParent_isReportedInJournalWithReason() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenReturn(awaitingParent("ждёт родителя: контрагент X"));

        scheduler().importPendingMessages();

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("сообщение " + message.receipt().messageId()
                            + " потока MKB10 ждёт родителя: контрагент X");
        });
    }

    @Test
    void overdueWaits_areRejectedAfterThePass() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenReturn(applied());

        scheduler().importPendingMessages();

        InOrder order = inOrder(importService, overdueParentWaits);
        order.verify(importService).importMessage(message);
        order.verify(overdueParentWaits).rejectAndReport();
    }

    @Test
    void failedMessage_doesNotStopThePass() {
        InboundMessage broken = received();
        InboundMessage next = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(broken, next));
        doThrow(new OneCFieldException("не задано поле code")).when(importService).importMessage(broken);
        when(importService.importMessage(next)).thenReturn(applied());

        scheduler().importPendingMessages();

        verify(importService).importMessage(next);
    }

    private ReferenceImportScheduler scheduler() {
        return new ReferenceImportScheduler(repository, importService, inboundMessageService,
                new ParseFailures(new DatabaseOutages(new HibernateExceptionTranslator())), overdueParentWaits,
                ImportSettings.defaults().batchSize(BATCH_SIZE).build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InboundMessage received() {
        return InboundMessage.receive(new InboundReceipt(UUID.randomUUID(), OneCStream.MKB10, QUEUE, NOW), "{}");
    }

    private static Logger schedulerLog() {
        return (Logger) LoggerFactory.getLogger(ReferenceImportScheduler.class);
    }
}
