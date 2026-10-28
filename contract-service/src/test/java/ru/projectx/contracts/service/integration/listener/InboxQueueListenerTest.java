package ru.projectx.contracts.service.integration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.receipt.InboxReceiver;
import ru.projectx.contracts.service.inbox.receipt.ReceiptOutcome;
import ru.projectx.contracts.support.TaskBodies;
import tools.jackson.databind.json.JsonMapper;

class InboxQueueListenerTest {

    private static final InboxReceipt RECEIPT = new InboxReceipt(UUID.fromString(TaskBodies.MESSAGE_ID),
            UUID.fromString(TaskBodies.REQUEST_ID), "rvl.contract-task.upserted", "ContractTaskUpserted", 1);

    private final InboxReceiver receiver = mock();
    private final Channel channel = mock();
    private final InboxStorageFailures failures = mock();
    private final InboxMetrics metrics = mock();
    private final InboxQueueListener listener = new InboxQueueListener(
            new TaskEnvelopeParser(JsonMapper.builder().build()), receiver, failures, metrics);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(InboxQueueListener.class);

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
        verifyNoInteractions(failures, metrics);
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
    void storageFailure_isHandedToTheFailureRulesWithoutAnswerToTheBroker() throws IOException {
        String body = TaskBodies.upserted().toJson();
        IllegalStateException failure = new IllegalStateException("Тестова", new SQLException("locked", "55P03"));
        given(receiver.receive(RECEIPT, body)).willThrow(failure);

        listener.onMessage(message(body), channel);

        ArgumentCaptor<TaskDelivery> delivery = ArgumentCaptor.captor();
        verify(failures).handle(delivery.capture(), eq(RECEIPT), eq(failure));
        assertThat(delivery.getValue().queue()).isEqualTo("task-changes");
        verify(channel, never()).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, true);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void bodyWithUnstorableText_goesToQuarantineInsteadOfTheJournal() throws IOException {
        String body = TaskBodies.upserted().toJson().replace("Тестова", "Тест\\u0000ова");

        listener.onMessage(message(body), channel);

        ArgumentCaptor<TaskDelivery> delivery = ArgumentCaptor.captor();
        verify(failures).quarantine(delivery.capture(), eq(RECEIPT),
                eq("в теле символ, который база не хранит: нулевой или непарный суррогат"));
        assertThat(delivery.getValue().payload()).isEqualTo(body);
        verify(receiver, never()).receive(any(), anyString());
        verifyNoInteractions(metrics);
        verify(channel, never()).basicAck(7L, false);
    }

    @Test
    void unreadableBody_isAcknowledgedLostAndCountedByItsOwnReason() throws IOException {
        listener.onMessage(message("<не JSON> Тестова"), channel);

        verify(channel).basicAck(7L, false);
        verify(metrics).countLost("unreadable_body");
        verifyNoInteractions(receiver, failures);
        assertThat(journal.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("потеряно: тело не читается как JSON").doesNotContain("Тестова"));
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
