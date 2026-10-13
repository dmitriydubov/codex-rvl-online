package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.InboxReceiver;
import ru.projectx.contracts.service.inbox.ReceiptOutcome;
import ru.projectx.contracts.support.TaskBodies;
import tools.jackson.databind.json.JsonMapper;

class TaskQueueListenerTest {

    private static final InboxReceipt RECEIPT = new InboxReceipt(UUID.fromString(TaskBodies.MESSAGE_ID),
            UUID.fromString(TaskBodies.REQUEST_ID), "rvl.contract-task.upserted", "ContractTaskUpserted", 1);

    private static final Duration REQUEUE_DELAY = Duration.ofSeconds(5);

    private final InboxReceiver receiver = mock();
    private final Channel channel = mock();
    private final Pauses pauses = mock();
    private final InboxMetrics metrics = mock();
    private final TaskQueueListener listener = new TaskQueueListener(
            new TaskEnvelopeParser(JsonMapper.builder().build()), receiver,
            new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                    new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                    REQUEUE_DELAY), pauses, metrics);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(TaskQueueListener.class);

    @BeforeEach
    void collectJournal() {
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void storedMessage_isAcknowledgedAfterTheReceiverReturns() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willReturn(ReceiptOutcome.STORED);

        listener.onMessage(message(body), channel);

        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, true);
    }

    @Test
    void duplicateMessage_isAcknowledgedWithoutSecondStore() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willReturn(ReceiptOutcome.DUPLICATE);

        listener.onMessage(message(body), channel);

        verify(channel).basicAck(7L, false);
    }

    @Test
    void unusableEnvelope_isAcknowledgedLostAndLoggedWithoutTheBody() throws IOException {
        String body = TaskBodies.upserted().withoutMetaField("message_id").toJson();

        listener.onMessage(message(body), channel);

        verify(channel).basicAck(7L, false);
        verify(receiver, never()).receive(any(), anyString());
        verify(metrics).countLost("unusable_envelope");
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("сообщение очереди task-changes потеряно: в конверте нет пригодного message_id")
                    .contains("(" + body.getBytes().length + " байт)")
                    .doesNotContain("Тестова");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void storageFailure_returnsMessageToQueueAfterThePauseAndLogsTypeAndSqlStateOnly() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body))
                .willThrow(new IllegalStateException("Тестова", new SQLException("locked", "55P03")));
        given(pauses.hold(REQUEUE_DELAY)).willReturn(true);

        listener.onMessage(message(body), channel);

        verify(pauses).hold(REQUEUE_DELAY);
        verify(channel).basicNack(7L, false, true);
        verify(channel, never()).basicAck(7L, false);
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains(TaskBodies.MESSAGE_ID)
                    .contains("java.lang.IllegalStateException, SQLSTATE 55P03")
                    .doesNotContain("Тестова");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void interruptedPause_leavesMessageUnacknowledgedForTheBrokerToRequeue() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willThrow(new IllegalStateException("база"));
        given(pauses.hold(REQUEUE_DELAY)).willReturn(false);

        listener.onMessage(message(body), channel);

        verify(channel, never()).basicNack(7L, false, true);
        verify(channel, never()).basicAck(7L, false);
    }

    @Test
    void bodyWithUnstorableText_isAcknowledgedLostAndLoggedWithoutTheBody() throws IOException {
        String body = TaskBodies.upserted().toJson().replace("Тестова", "Тест\\u0000ова");

        listener.onMessage(message(body), channel);

        verify(channel).basicAck(7L, false);
        verify(receiver, never()).receive(any(), anyString());
        verify(metrics).countLost("unstorable_text");
        verifyNoInteractions(pauses);
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("потеряно: в теле символ, который база не хранит: нулевой или непарный суррогат")
                    .doesNotContain("Тест");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void unreadableBody_isAcknowledgedLostAndCountedByItsOwnReason() throws IOException {
        listener.onMessage(message("<не JSON> Тестова"), channel);

        verify(channel).basicAck(7L, false);
        verify(metrics).countLost("unreadable_body");
        verifyNoInteractions(receiver);
        assertThat(journal.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("потеряно: тело не читается как JSON").doesNotContain("Тестова"));
    }

    @Test
    void storedMessage_countsNoLoss() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willReturn(ReceiptOutcome.STORED);

        listener.onMessage(message(body), channel);

        verifyNoInteractions(metrics);
    }

    @Test
    void closedChannelOnAcknowledge_isLoggedByTypeOnlyAndNothingIsThrown() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willReturn(ReceiptOutcome.STORED);
        willThrow(new AlreadyClosedException(new ShutdownSignalException(false, false, null, channel)))
                .given(channel).basicAck(7L, false);

        listener.onMessage(message(body), channel);

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("не отправлен: com.rabbitmq.client.AlreadyClosedException");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void closedChannelOnReturn_isLoggedByTypeOnlyAndNothingIsThrown() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willThrow(new IllegalStateException("Тестова"));
        given(pauses.hold(REQUEUE_DELAY)).willReturn(true);
        willThrow(new AlreadyClosedException(new ShutdownSignalException(false, false, null, channel)))
                .given(channel).basicNack(7L, false, true);

        listener.onMessage(message(body), channel);

        assertThat(journal.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.WARN);
        assertThat(journal.list.get(1).getFormattedMessage())
                .contains("не отправлен: com.rabbitmq.client.AlreadyClosedException").doesNotContain("Тестова");
    }

    @Test
    void closedChannel_isLoggedWithoutTheBodyAndNothingIsThrown() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willReturn(ReceiptOutcome.STORED);
        willThrow(new IOException("Тестова: канал закрыт")).given(channel).basicAck(7L, false);

        listener.onMessage(message(body), channel);

        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("ответ брокеру по сообщению очереди task-changes не отправлен: java.io.IOException")
                    .doesNotContain("Тестова");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("task-changes");
        properties.setDeliveryTag(7L);
        return new Message(body.getBytes(), properties);
    }
}
