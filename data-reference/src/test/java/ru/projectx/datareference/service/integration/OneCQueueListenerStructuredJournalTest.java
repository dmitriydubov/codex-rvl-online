package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.config.OneCJson;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.QuarantineService;
import ru.projectx.datareference.service.onec.OneCIngestService;
import ru.projectx.datareference.support.StructuredJournal;
import tools.jackson.databind.JsonNode;

/**
 * Записи слушателя очередей 1С:УСК в рабочем формате журнала несут поток и очередь отдельными полями.
 *
 * <p>Это записи, по которым настроены оповещения: приём приостановлен, сообщение в карантине, сообщение
 * потеряно. По полям {@code stream} и {@code queue} дежурный сразу видит, какой поток стоит. Тела сообщения в
 * записи нет: через слушатель идут полисы с персональными данными. Пауза и карантин здесь настоящие, подменены
 * только запись в базу и канал брокера.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OneCQueueListenerStructuredJournalTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000c");
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    /** Похоже на персональные данные из полиса, то есть ровно на то, чего в журнале быть не должно. */
    private static final String PERSONAL_DATA = "Иванова Мария Петровна";

    @Mock
    private OneCIngestService ingestService;

    @Mock
    private QuarantineService quarantineService;

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> containers;

    @Mock
    private IngestStorageProbe probe;

    @Mock
    private Channel channel;

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
    void pause_carriesStreamAndQueueButNoBody(CapturedOutput output) throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataAccessResourceFailureException("обрыв на строке " + PERSONAL_DATA,
                        new SQLException("обрыв на строке " + PERSONAL_DATA, "08006")));

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID.toString())), channel);

        JsonNode warning = StructuredJournal.recordWith(output, "приём из 1С:УСК приостановлен");
        assertThat(warning.path("log").path("level").asString()).isEqualTo("WARN");
        assertThatCarriesStreamAndQueue(warning);
        assertThat(output.getAll()).doesNotContain(PERSONAL_DATA);
    }

    @Test
    void quarantine_carriesStreamQueueAndMessageButNoBody(CapturedOutput output) throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataIntegrityViolationException("unsupported escape in " + PERSONAL_DATA,
                        new SQLException("unsupported escape in " + PERSONAL_DATA, "22P05")));
        when(quarantineService.quarantine(any(), any())).thenReturn(true);

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID.toString())), channel);

        JsonNode error = StructuredJournal.recordWith(output, "в карантине");
        assertThat(error.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThatCarriesStreamAndQueue(error);
        assertThat(error.path(OneCLogContext.MESSAGE_ID).asString()).isEqualTo(MESSAGE_ID.toString());
        assertThat(output.getAll()).doesNotContain(PERSONAL_DATA);
    }

    /** У потерянного сообщения нет пригодного {@code message-id}, поэтому и поля с ним в записи нет. */
    @Test
    void lostMessage_carriesStreamAndQueueWithoutMessageId(CapturedOutput output) throws IOException {
        listener().receive(OneCStream.POLICIES, delivery(envelope("00000000-0000-0000-0000-000000000000")), channel);

        JsonNode error = StructuredJournal.recordWith(output, "потеряно");
        assertThat(error.path("log").path("level").asString()).isEqualTo("ERROR");
        assertThatCarriesStreamAndQueue(error);
        assertThat(error.has(OneCLogContext.MESSAGE_ID)).isFalse();
        assertThat(output.getAll()).doesNotContain(PERSONAL_DATA);
    }

    private static void assertThatCarriesStreamAndQueue(JsonNode record) {
        assertThat(record.path(OneCLogContext.STREAM).asString()).isEqualTo(OneCStream.POLICIES.name());
        assertThat(record.path(OneCLogContext.QUEUE).asString()).isEqualTo(QUEUE);
    }

    private final OneCIngestMetrics metrics = IngestTestMetrics.discarded();

    private OneCQueueListener listener() {
        OneCConsumerPause pause = new OneCConsumerPause(containers, probe, Clock.fixed(NOW, ZoneOffset.UTC), metrics);
        return new OneCQueueListener(
                new OneCEnvelopeParser(OneCJson.mapper()),
                ingestService,
                new OneCStorageFailures(quarantineService, pause,
                        new IngestProperties(Duration.ofMillis(1), Duration.ofSeconds(10)), metrics),
                metrics);
    }

    private static String envelope(String messageId) {
        return """
                {"data":{"fio":"%s"},"metadata":{"message-id":"%s","producer":"1c-usk"}}"""
                .formatted(PERSONAL_DATA, messageId);
    }

    private static Message delivery(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(1L);
        properties.setConsumerQueue(QUEUE);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
