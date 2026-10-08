package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.config.OneCJson;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.QuarantineService;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Защищает журнал слушателя очередей 1С:УСК: на время обработки в MDC лежат {@code messageId}, {@code stream} и
 * {@code queue}, а тела сообщения и текста чужого исключения в журнале нет, потому что через слушатель идут полисы
 * с персональными данными, а ошибка вставки печатает параметры запроса.
 */
@ExtendWith(MockitoExtension.class)
class OneCQueueListenerJournalTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000c");

    /** Похоже на персональные данные из полиса, то есть ровно на то, чего в журнале быть не должно. */
    private static final String PERSONAL_DATA = "Иванова Мария Петровна";

    /** Ключ MDC, который положил не слушатель, а, например, трассировка. */
    private static final String FOREIGN_MDC_KEY = "traceId";
    private static final String FOREIGN_MDC_VALUE = "0af7651916cd43dd8448eb211c80319c";

    @Mock
    private OneCIngestService ingestService;

    @Mock
    private QuarantineService quarantineService;

    @Mock
    private OneCConsumerPause consumers;

    @Mock
    private Channel channel;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        listenerLog().addAppender(logged);
        listenerLog().setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLog() {
        listenerLog().detachAppender(logged);
        listenerLog().setLevel(null);
        logged.stop();
    }

    @AfterEach
    void releaseForeignMdcKey() {
        MDC.remove(FOREIGN_MDC_KEY);
    }

    @Test
    void processing_carriesMessageStreamAndQueueInMdc() throws IOException {
        Map<String, String> seen = new HashMap<>();
        when(ingestService.receive(any(), any(), any(), any())).thenAnswer(invocation -> {
            seen.putAll(MDC.getCopyOfContextMap());
            return true;
        });

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(seen).containsEntry(OneCLogContext.MESSAGE_ID, MESSAGE_ID.toString())
                .containsEntry(OneCLogContext.STREAM, OneCStream.POLICIES.name())
                .containsEntry(OneCLogContext.QUEUE, QUEUE);
    }

    /** Поток контейнера обработает следующее сообщение, и ключи этого сообщения ему не достаются. */
    @Test
    void mdc_isClearedAfterProcessing() throws IOException {
        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(MDC.get(OneCLogContext.MESSAGE_ID)).isNull();
        assertThat(MDC.get(OneCLogContext.STREAM)).isNull();
        assertThat(MDC.get(OneCLogContext.QUEUE)).isNull();
    }

    /** В журнал попадает тип исключения, но не его текст: текст ошибки вставки печатает параметры запроса. */
    @Test
    void storageFailure_logsExceptionTypeButNotItsText() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataIntegrityViolationException("ошибка вставки, параметры: " + PERSONAL_DATA,
                        new SQLException("параметры: " + PERSONAL_DATA, "23505")));

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        ILoggingEvent warning = onlyEvent(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains(DataIntegrityViolationException.class.getName())
                .doesNotContain(PERSONAL_DATA);
        assertThat(warning.getThrowableProxy()).isNull();
        assertThat(warning.getMDCPropertyMap())
                .containsEntry(OneCLogContext.MESSAGE_ID, MESSAGE_ID.toString());
    }

    /** Отказ хранилища, ставящий приём на паузу: WARN с типом и кодом SQLSTATE, без текста ошибки. */
    @Test
    void storageOutage_logsTypeAndSqlStateButNotText() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataAccessResourceFailureException("обрыв на строке " + PERSONAL_DATA,
                        new SQLException("обрыв на строке " + PERSONAL_DATA, "08006")));

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        ILoggingEvent warning = onlyEvent(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains(DataAccessResourceFailureException.class.getName(), "08006")
                .doesNotContain(PERSONAL_DATA);
        assertThat(warning.getThrowableProxy()).isNull();
        assertThat(warning.getMDCPropertyMap())
                .containsEntry(OneCLogContext.MESSAGE_ID, MESSAGE_ID.toString());
    }

    /**
     * Карантин оставляет ERROR с кодом отказа и размером тела, но без самого тела и без текста ошибки базы:
     * оба несут значения строки.
     */
    @Test
    void quarantine_logsReasonAndSizeButNoBody() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataIntegrityViolationException("unsupported escape in " + PERSONAL_DATA,
                        new SQLException("unsupported escape in " + PERSONAL_DATA, "22P05")));
        when(quarantineService.quarantine(any(), any())).thenReturn(true);

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        ILoggingEvent error = onlyEvent(Level.ERROR);
        assertThat(error.getFormattedMessage())
                .contains("карантин", "22P05", MESSAGE_ID.toString(), QUEUE)
                .doesNotContain(PERSONAL_DATA);
        assertThat(error.getThrowableProxy()).isNull();
        assertThat(error.getMDCPropertyMap())
                .containsEntry(OneCLogContext.MESSAGE_ID, MESSAGE_ID.toString());
    }

    /** Повтор уже карантинного сообщения — не событие: ни ERROR, ни WARN. */
    @Test
    void redeliveredQuarantinedMessage_leavesNoJournalEntry() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataIntegrityViolationException("escape", new SQLException("escape", "22P05")));
        when(quarantineService.quarantine(any(), any())).thenReturn(false);

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(logged.list).isEmpty();
    }

    /**
     * От потерянного сообщения остаётся только строка журнала, поэтому она несёт поток и очередь. Тела
     * в ней нет: у нечитаемого конверта внутри может оказаться полис.
     */
    @Test
    void unreadableEnvelope_logsStreamAndQueueButNoBody() throws IOException {
        listener().receive(OneCStream.POLICIES, delivery("{\"data\":{\"fio\":\"" + PERSONAL_DATA + "\""), channel);

        ILoggingEvent error = onlyEvent(Level.ERROR);
        assertThat(error.getFormattedMessage()).doesNotContain(PERSONAL_DATA);
        assertThat(error.getThrowableProxy()).isNull();
        assertThat(error.getMDCPropertyMap())
                .containsEntry(OneCLogContext.STREAM, OneCStream.POLICIES.name())
                .containsEntry(OneCLogContext.QUEUE, QUEUE)
                .doesNotContainKey(OneCLogContext.MESSAGE_ID);
    }

    /**
     * Сообщение, потерянное из-за message-id не в канонической записи, оставляет ERROR «потеряно» с
     * причиной, но без самого значения и без тела.
     */
    @Test
    void nonCanonicalMessageId_isLoggedAsLostWithoutItsValue() throws IOException {
        String messageId = "9f1c0f2e-0000-4000-8000-+0000000000c";

        listener().receive(OneCStream.POLICIES, delivery(envelope(messageId)), channel);

        ILoggingEvent error = onlyEvent(Level.ERROR);
        assertThat(error.getFormattedMessage())
                .contains("потеряно", "metadata.message-id не является GUID")
                .doesNotContain(messageId, PERSONAL_DATA);
        assertThat(error.getThrowableProxy()).isNull();
        assertThat(error.getMDCPropertyMap()).doesNotContainKey(OneCLogContext.MESSAGE_ID);
    }

    /**
     * Сообщение с нулевым message-id потеряно так же громко, как сообщение без message-id: в журнале
     * остаётся ERROR «потеряно» с причиной, а не DEBUG о дубле.
     */
    @Test
    void zeroMessageId_isLoggedAsLost() throws IOException {
        listener().receive(OneCStream.POLICIES, delivery(envelope("00000000-0000-0000-0000-000000000000")), channel);

        ILoggingEvent error = onlyEvent(Level.ERROR);
        assertThat(error.getFormattedMessage())
                .contains("потеряно", "в metadata нет обязательного message-id")
                .doesNotContain(PERSONAL_DATA);
        assertThat(error.getMDCPropertyMap()).doesNotContainKey(OneCLogContext.MESSAGE_ID);
    }

    @Test
    void duplicate_isReportedAsDuplicate() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenReturn(false);

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(onlyEvent(Level.DEBUG).getFormattedMessage()).contains(MESSAGE_ID.toString(), "дубль");
    }

    @Test
    void newMessage_leavesNoJournalEntry() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenReturn(true);

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(logged.list).isEmpty();
    }

    /**
     * Канал может отказать и на подтверждении, если оборвалось соединение с брокером. Ошибка уходит
     * контейнеру, но ключи сообщения с потока всё равно снимаются. Иначе следующее сообщение этого
     * потока писало бы в журнал чужой {@code messageId}.
     */
    @Test
    void mdc_isClearedWhenAcknowledgementFails() throws IOException {
        doThrow(new IOException("канал закрыт")).when(channel).basicAck(anyLong(), anyBoolean());

        assertThatIOException().isThrownBy(
                () -> listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel));

        assertNoMessageKeysInMdc();
    }

    @Test
    void mdc_isClearedWhenReturnToQueueFails() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataIntegrityViolationException("duplicate key", new SQLException("duplicate key", "23505")));
        doThrow(new IOException("канал закрыт")).when(channel).basicNack(anyLong(), anyBoolean(), anyBoolean());

        assertThatIOException().isThrownBy(
                () -> listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel));

        assertNoMessageKeysInMdc();
    }

    @Test
    void mdc_isClearedWhenDroppingUnreadableMessageFails() throws IOException {
        doThrow(new IOException("канал закрыт")).when(channel).basicAck(anyLong(), anyBoolean());

        assertThatIOException().isThrownBy(
                () -> listener().receive(OneCStream.POLICIES, delivery("не JSON"), channel));

        assertNoMessageKeysInMdc();
    }

    /**
     * Слушатель снимает только свои ключи. То, что положили в MDC до него контейнер или трассировка,
     * остаётся на месте.
     */
    @Test
    void foreignMdcKeys_surviveProcessing() throws IOException {
        MDC.put(FOREIGN_MDC_KEY, FOREIGN_MDC_VALUE);

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(MDC.get(FOREIGN_MDC_KEY)).isEqualTo(FOREIGN_MDC_VALUE);
    }

    private static void assertNoMessageKeysInMdc() {
        assertThat(MDC.get(OneCLogContext.MESSAGE_ID)).isNull();
        assertThat(MDC.get(OneCLogContext.STREAM)).isNull();
        assertThat(MDC.get(OneCLogContext.QUEUE)).isNull();
    }

    private ILoggingEvent onlyEvent(Level level) {
        assertThat(logged.list).hasSize(1);
        ILoggingEvent event = logged.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(level);
        return event;
    }

    private final OneCIngestMetrics metrics = IngestTestMetrics.discarded();

    private OneCQueueListener listener() {
        return new OneCQueueListener(
                new OneCEnvelopeParser(OneCJson.mapper()),
                ingestService,
                new OneCStorageFailures(quarantineService, consumers,
                        new IngestProperties(Duration.ofMillis(1), Duration.ofSeconds(10)), metrics),
                metrics);
    }

    /** Журнал всего пакета приёма: слушатель и его помощник по отказам записи пишут в свои категории. */
    private static Logger listenerLog() {
        return (Logger) LoggerFactory.getLogger(OneCQueueListener.class.getPackageName());
    }

    private static String envelope(UUID messageId) {
        return envelope(messageId.toString());
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
