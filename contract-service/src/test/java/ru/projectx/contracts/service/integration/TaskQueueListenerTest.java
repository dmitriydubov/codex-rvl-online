package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.Channel;
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
import ru.projectx.contracts.service.inbox.InboxReceiver;
import ru.projectx.contracts.service.inbox.ReceiptOutcome;
import ru.projectx.contracts.support.TaskBodies;
import tools.jackson.databind.json.JsonMapper;

class TaskQueueListenerTest {

    private static final InboxReceipt RECEIPT = new InboxReceipt(UUID.fromString(TaskBodies.MESSAGE_ID),
            UUID.fromString(TaskBodies.REQUEST_ID), "rvl.contract-task.upserted", "ContractTaskUpserted", 1);

    private final InboxReceiver receiver = mock();
    private final Channel channel = mock();
    private final TaskQueueListener listener = new TaskQueueListener(
            new TaskEnvelopeParser(JsonMapper.builder().build()), receiver,
            new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                    new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                    Duration.ZERO));
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
    void storageFailure_returnsMessageToQueueAndLogsTypeAndSqlStateOnly() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body))
                .willThrow(new IllegalStateException("Тестова", new SQLException("locked", "55P03")));

        listener.onMessage(message(body), channel);

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
    void interruptedPause_stillReturnsMessageAndKeepsInterruptFlag() throws IOException {
        String body = TaskBodies.upserted().toJson();
        given(receiver.receive(RECEIPT, body)).willThrow(new IllegalStateException("база"));
        Thread.currentThread().interrupt();

        listener.onMessage(message(body), channel);

        assertThat(Thread.interrupted()).isTrue();
        verify(channel).basicNack(7L, false, true);
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("task-changes");
        properties.setDeliveryTag(7L);
        return new Message(body.getBytes(), properties);
    }
}
