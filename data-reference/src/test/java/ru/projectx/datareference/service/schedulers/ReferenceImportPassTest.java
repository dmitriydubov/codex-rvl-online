package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.onec.ReferenceImportService;

/**
 * Проход шедулера разбора без базы: что он берёт, кому отдаёт и как классифицирует отказ.
 * Разбор и запись исхода — у соседей, поэтому они подменены; на настоящей базе тот же проход
 * проверяет {@link ReferenceImportSchedulerTest}.
 *
 * <p>Журнал проверяется целиком: запись о временной ошибке обязана нести тип отказа и не нести
 * его текста — через этот путь идут и полисы, а текст ошибки вставки печатает параметры запроса.
 */
@ExtendWith(MockitoExtension.class)
class ReferenceImportPassTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final int BATCH_SIZE = 7;
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private ReferenceImportService importService;

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

    /** Пачка дозревших на момент прохода, не больше заданного размера, — и каждое сообщение в разбор. */
    @Test
    void everyDueMessage_isImported() {
        InboundMessage first = received();
        InboundMessage second = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(first, second));

        scheduler().importPendingMessages();

        verify(importService).importMessage(first);
        verify(importService).importMessage(second);
        verifyNoInteractions(inboundMessageService);
        assertThat(logged.list).isEmpty();
    }

    /** Нарушение контракта постоянно: строка сразу в терминальный отказ, с причиной. */
    @Test
    void contractViolation_isRecordedAsPermanentFailure() {
        InboundMessage message = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(new OneCFieldException("не задано поле code")).when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordPermanentFailure(message.messageId(), "не задано поле code");
        verify(inboundMessageService, never()).recordTemporaryFailure(any(), any());
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("сообщение " + message.messageId()
                    + " потока MKB10 не соответствует контракту, повтор бессмыслен: не задано поле code");
        });
    }

    /** Любой другой отказ временный: полный текст — в строку сырья, в журнал — только тип. */
    @Test
    void otherFailure_isRecordedAsTemporaryWithoutItsTextInLog() {
        InboundMessage message = received();
        CannotAcquireLockException failure = new CannotAcquireLockException("параметры запроса: [значения полей]");
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(message));
        doThrow(failure).when(importService).importMessage(message);

        scheduler().importPendingMessages();

        verify(inboundMessageService).recordTemporaryFailure(message.messageId(), failure.toString());
        verify(inboundMessageService, never()).recordPermanentFailure(any(), any());
        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).isEqualTo("временная ошибка разбора сообщения "
                    + message.messageId() + " потока MKB10: org.springframework.dao.CannotAcquireLockException");
        });
    }

    /** Один отказ не останавливает пачку: следующее сообщение разбирается как обычно. */
    @Test
    void failedMessage_doesNotStopThePass() {
        InboundMessage broken = received();
        InboundMessage next = received();
        when(repository.findDueForImport(NOW, Limit.of(BATCH_SIZE))).thenReturn(List.of(broken, next));
        doThrow(new OneCFieldException("не задано поле code")).when(importService).importMessage(broken);

        scheduler().importPendingMessages();

        verify(importService).importMessage(next);
    }

    private ReferenceImportScheduler scheduler() {
        return new ReferenceImportScheduler(repository, importService, inboundMessageService,
                new ImportProperties(Duration.ofSeconds(10), Duration.ofSeconds(30), BATCH_SIZE, Duration.ofHours(1),
                        Duration.ofDays(30), 500),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InboundMessage received() {
        return InboundMessage.receive(UUID.randomUUID(), OneCStream.MKB10, QUEUE, "{}", NOW);
    }

    private static Logger schedulerLog() {
        return (Logger) LoggerFactory.getLogger(ReferenceImportScheduler.class);
    }
}
