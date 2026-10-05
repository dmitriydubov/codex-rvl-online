package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.projectx.datareference.service.onec.ImportResult.applied;

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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.onec.ReferenceImportService;
import ru.projectx.datareference.support.ImportSettings;
import ru.projectx.datareference.support.StructuredJournal;
import tools.jackson.databind.JsonNode;

/**
 * Запись об исходе разбора сообщения в рабочем формате журнала несёт {@code messageId} и поток отдельными
 * полями: по ним исход находится тем же поиском, что и приём этого сообщения из брокера.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ReferenceImportSchedulerStructuredJournalTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

    /** Похоже на персональные данные, то есть ровно на то, чего в журнале быть не должно. */
    private static final String PERSONAL_DATA = "Иванова Мария Петровна";

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private ReferenceImportService importService;

    @Mock
    private InboundMessageService inboundMessageService;

    private StructuredJournal journal;

    @BeforeEach
    void startJournal() {
        journal = StructuredJournal.start();
    }

    @AfterEach
    void stopJournal() {
        journal.close();
    }

    @Test
    void importOutcome_carriesMessageIdAndStream(CapturedOutput output) {
        InboundMessage message = InboundMessage.receive(UUID.randomUUID(), OneCStream.MKB10, QUEUE, "{}", NOW);
        when(repository.findDueForImport(any(), any())).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenReturn(applied());

        scheduler().importPendingMessages();

        JsonNode outcome = StructuredJournal.recordWith(output, "применено");
        assertThat(outcome.path(OneCLogContext.MESSAGE_ID).asString()).isEqualTo(message.messageId().toString());
        assertThat(outcome.path(OneCLogContext.STREAM).asString()).isEqualTo(OneCStream.MKB10.name());
    }

    /**
     * Текст временного отказа в журнал не попадает ни строкой, ни разобранным исключением: рабочий формат
     * журнала выводит приложенное к записи исключение полями {@code error.message} и {@code error.stack_trace},
     * а текст ошибки вставки печатает значения полей.
     */
    @Test
    void temporaryFailure_carriesMessageIdAndStreamButNoFailureText(CapturedOutput output) {
        InboundMessage message = InboundMessage.receive(UUID.randomUUID(), OneCStream.MKB10, QUEUE, "{}", NOW);
        when(repository.findDueForImport(any(), any())).thenReturn(List.of(message));
        when(importService.importMessage(message)).thenThrow(
                new CannotAcquireLockException("параметры запроса: [" + PERSONAL_DATA + "]"));

        scheduler().importPendingMessages();

        JsonNode warning = StructuredJournal.recordWith(output, "временная ошибка разбора");
        assertThat(warning.path("log").path("level").asString()).isEqualTo("WARN");
        assertThat(warning.path(OneCLogContext.MESSAGE_ID).asString()).isEqualTo(message.messageId().toString());
        assertThat(warning.path(OneCLogContext.STREAM).asString()).isEqualTo(OneCStream.MKB10.name());
        assertThat(warning.has("error")).isFalse();
        assertThat(output.getAll()).doesNotContain(PERSONAL_DATA);
    }

    private ReferenceImportScheduler scheduler() {
        return new ReferenceImportScheduler(repository, importService, inboundMessageService,
                ImportSettings.defaults().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
