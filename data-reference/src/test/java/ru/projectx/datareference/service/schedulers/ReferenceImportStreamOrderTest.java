package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.projectx.datareference.service.onec.ImportOutcome.applied;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Limit;
import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.service.database.DatabaseOutages;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.service.inbound.ParseFailures;
import ru.projectx.datareference.service.onec.ReferenceImportService;
import ru.projectx.datareference.support.ImportSettings;

@ExtendWith(MockitoExtension.class)
class ReferenceImportStreamOrderTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final int BATCH_SIZE = 7;
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

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

    @AfterEach
    void releaseLog() {
        schedulerLog().detachAppender(logged);
        logged.stop();
    }

    @Test
    void messageLeftForRetry_holdsLaterMessagesOfItsStreamInThisPass() {
        InboundMessage head = received(OneCStream.MKB10);
        InboundMessage sameStream = received(OneCStream.MKB10);
        InboundMessage otherStream = received(OneCStream.INSURANCE_CONTRACTS);
        due(head, sameStream, otherStream);
        headFailsTemporarily(head, InboundStatus.RETRY);
        when(importService.importMessage(otherStream)).thenReturn(applied());

        scheduler().importPendingMessages();

        verify(importService, never()).importMessage(sameStream);
        verify(importService).importMessage(otherStream);
    }

    @Test
    void heldMessage_isReportedInJournal() {
        InboundMessage head = received(OneCStream.MKB10);
        InboundMessage held = received(OneCStream.MKB10);
        due(head, held);
        headFailsTemporarily(head, InboundStatus.RETRY);

        scheduler().importPendingMessages();

        assertThat(logged.list).last().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("сообщение " + held.receipt().messageId()
                    + " потока MKB10 ждёт: раньше него в потоке сообщение " + head.receipt().messageId()
                    + ", отложенное до повтора");
        });
    }

    @Test
    void messageFailedAfterLastRetry_doesNotHoldItsStream() {
        InboundMessage head = received(OneCStream.MKB10);
        InboundMessage next = received(OneCStream.MKB10);
        due(head, next);
        headFailsTemporarily(head, InboundStatus.FAILED);
        when(importService.importMessage(next)).thenReturn(applied());

        scheduler().importPendingMessages();

        verify(importService).importMessage(next);
    }

    @Test
    void failureAfterCommittedOutcome_doesNotHoldItsStream() {
        InboundMessage head = received(OneCStream.MKB10);
        InboundMessage next = received(OneCStream.MKB10);
        due(head, next);
        headFailsTemporarily(head, InboundStatus.PROCESSED);
        when(importService.importMessage(next)).thenReturn(applied());

        scheduler().importPendingMessages();

        verify(importService).importMessage(next);
    }

    @Test
    void permanentFailure_doesNotHoldItsStream() {
        InboundMessage head = received(OneCStream.MKB10);
        InboundMessage next = received(OneCStream.MKB10);
        due(head, next);
        doThrow(new OneCFieldException("нет обязательного поля name")).when(importService).importMessage(head);
        when(inboundMessageService.recordPermanentFailure(eq(head.receipt().messageId()), any(), any()))
                .thenReturn(InboundStatus.FAILED);
        when(importService.importMessage(next)).thenReturn(applied());

        scheduler().importPendingMessages();

        verify(importService).importMessage(next);
    }

    @Test
    void heldStream_isFreeInTheNextPass() {
        InboundMessage head = received(OneCStream.MKB10);
        InboundMessage next = received(OneCStream.MKB10);
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(head)).thenReturn(List.of(next));
        headFailsTemporarily(head, InboundStatus.RETRY);
        when(importService.importMessage(next)).thenReturn(applied());
        ReferenceImportScheduler scheduler = scheduler();

        scheduler.importPendingMessages();
        scheduler.importPendingMessages();

        verify(importService).importMessage(next);
    }

    private void due(InboundMessage... messages) {
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(messages));
    }

    private void headFailsTemporarily(InboundMessage head, InboundStatus recorded) {
        doThrow(new CannotAcquireLockException("lock")).when(importService).importMessage(head);
        when(inboundMessageService.recordTemporaryFailure(eq(head.receipt().messageId()), any(), any()))
                .thenReturn(recorded);
    }

    private ReferenceImportScheduler scheduler() {
        return new ReferenceImportScheduler(repository, importService, inboundMessageService,
                new ParseFailures(new DatabaseOutages(new HibernateExceptionTranslator())), overdueParentWaits,
                ImportSettings.defaults().batchSize(BATCH_SIZE).build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InboundMessage received(OneCStream stream) {
        return InboundMessage.receive(new InboundReceipt(UUID.randomUUID(), stream, QUEUE, NOW), "{}");
    }

    private static Logger schedulerLog() {
        return (Logger) LoggerFactory.getLogger(ReferenceImportScheduler.class);
    }
}
