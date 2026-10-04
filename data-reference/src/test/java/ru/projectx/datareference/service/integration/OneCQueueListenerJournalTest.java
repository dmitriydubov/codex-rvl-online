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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.config.JacksonConfig;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Защищает то, что слушатель очередей 1С:УСК оставляет в журнале.
 *
 * <p>Важны две вещи. По строке журнала находится её сообщение: на время обработки в MDC лежат
 * {@code messageId}, {@code stream} и {@code queue}. И в журнал не попадает ни тело сообщения, ни
 * текст чужого исключения, потому что через слушатель идут полисы с персональными данными, а ошибка
 * вставки печатает параметры запроса.
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

        assertThat(seen).containsEntry(OneCQueueListener.MDC_MESSAGE_ID, MESSAGE_ID.toString())
                .containsEntry(OneCQueueListener.MDC_STREAM, OneCStream.POLICIES.name())
                .containsEntry(OneCQueueListener.MDC_QUEUE, QUEUE);
    }

    /** Поток контейнера обработает следующее сообщение, и ключи этого сообщения ему не достаются. */
    @Test
    void mdc_isClearedAfterProcessing() throws IOException {
        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(MDC.get(OneCQueueListener.MDC_MESSAGE_ID)).isNull();
        assertThat(MDC.get(OneCQueueListener.MDC_STREAM)).isNull();
        assertThat(MDC.get(OneCQueueListener.MDC_QUEUE)).isNull();
    }

    /** В журнал попадает тип исключения, но не его текст: текст ошибки вставки печатает параметры запроса. */
    @Test
    void storageFailure_logsExceptionTypeButNotItsText() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(
                new DataIntegrityViolationException("ошибка вставки, параметры: " + PERSONAL_DATA));

        listener().receive(OneCStream.POLICIES, delivery(envelope(MESSAGE_ID)), channel);

        ILoggingEvent warning = onlyEvent(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains(DataIntegrityViolationException.class.getName())
                .doesNotContain(PERSONAL_DATA);
        assertThat(warning.getThrowableProxy()).isNull();
        assertThat(warning.getMDCPropertyMap())
                .containsEntry(OneCQueueListener.MDC_MESSAGE_ID, MESSAGE_ID.toString());
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
                .containsEntry(OneCQueueListener.MDC_STREAM, OneCStream.POLICIES.name())
                .containsEntry(OneCQueueListener.MDC_QUEUE, QUEUE)
                .doesNotContainKey(OneCQueueListener.MDC_MESSAGE_ID);
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
        assertThat(error.getMDCPropertyMap()).doesNotContainKey(OneCQueueListener.MDC_MESSAGE_ID);
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
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("база недоступна"));
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
        assertThat(MDC.get(OneCQueueListener.MDC_MESSAGE_ID)).isNull();
        assertThat(MDC.get(OneCQueueListener.MDC_STREAM)).isNull();
        assertThat(MDC.get(OneCQueueListener.MDC_QUEUE)).isNull();
    }

    private ILoggingEvent onlyEvent(Level level) {
        assertThat(logged.list).hasSize(1);
        ILoggingEvent event = logged.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(level);
        return event;
    }

    private OneCQueueListener listener() {
        return new OneCQueueListener(
                new OneCEnvelopeParser(new JacksonConfig().objectMapper()),
                ingestService,
                new IngestProperties(Duration.ofMillis(1)));
    }

    private static Logger listenerLog() {
        return (Logger) LoggerFactory.getLogger(OneCQueueListener.class);
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
