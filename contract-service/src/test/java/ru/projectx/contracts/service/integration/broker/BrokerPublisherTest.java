package ru.projectx.contracts.service.integration.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.config.journal.OutboxProperties;
import ru.projectx.contracts.service.outbox.OutboundMessage;
import ru.projectx.contracts.service.outbox.PublishOutcome;

class BrokerPublisherTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final Duration CONFIRM_TIMEOUT = Duration.ofMillis(200);
    private static final OutboundMessage STATUS = new OutboundMessage(MESSAGE_ID, REQUEST, "ContractStatusChanged",
            "{\"data\": {}}");

    private final RabbitTemplate template = mock();
    private final BrokerPublisher publisher = new BrokerPublisher(
            new OutboundRoutes(template, new InternalBrokerProperties(
                    new BrokerConnectionProperties("localhost", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                            new BrokerConnectionProperties.Tls(false, true, true)),
                    true, new InternalBrokerProperties.Queues("tasks", "status-changes")),
                    mock(RabbitTemplate.class), new OneCBrokerProperties(
                    new BrokerConnectionProperties("localhost", 5675, "/", "user", "secret", Duration.ofSeconds(10),
                            new BrokerConnectionProperties.Tls(false, true, true)),
                    true, new OneCBrokerProperties.Queues("km-submissions", "km-registrations", "lm-requests",
                            "lm-issues")), List.of()),
            new OutboxProperties(new OutboxProperties.Publishing(Duration.ofSeconds(5), 100), CONFIRM_TIMEOUT,
                    new OutboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new OutboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500)));

    @Test
    void acknowledgedMessage_isDeliveredAsPersistentJsonToTheStatusQueueUnderItsMessageId() {
        brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(true, null)));

        PublishOutcome outcome = publisher.publish(STATUS);

        assertThat(outcome).isEqualTo(new PublishOutcome.Delivered());
        ArgumentCaptor<Message> sent = ArgumentCaptor.captor();
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.captor();
        verify(template).send(eq(""), eq("status-changes"), sent.capture(), correlation.capture());
        assertThat(correlation.getValue().getId()).isEqualTo(MESSAGE_ID.toString());
        assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"data\": {}}");
        MessageProperties properties = sent.getValue().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo(MESSAGE_ID.toString());
        assertThat(properties.getCorrelationId()).isEqualTo(REQUEST.toString());
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(MessageProperties.DEFAULT_DELIVERY_MODE).as("persistent — умолчание Spring AMQP")
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void returnedMessage_isRefusedWithTheReplyCodeEvenThoughTheBrokerAcknowledgedIt() {
        brokerAnswers(correlation -> {
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "", "status-changes"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        assertThat(publisher.publish(STATUS))
                .isEqualTo(new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)"));
    }

    @Test
    void negativelyAcknowledgedMessage_isRejectedWithTheBrokerReason() {
        brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "очередь переполнена")));

        assertThat(publisher.publish(STATUS))
                .isEqualTo(new PublishOutcome.Rejected("брокер отклонил сообщение (nack): очередь переполнена"));
    }

    @Test
    void negativelyAcknowledgedMessageWithoutReason_isRejected() {
        brokerAnswers(correlation -> correlation.getFuture().complete(new CorrelationData.Confirm(false, null)));

        assertThat(publisher.publish(STATUS)).isEqualTo(new PublishOutcome.Rejected("брокер отклонил сообщение (nack)"));
    }

    @Test
    void silentBroker_leavesTheMessageUnconfirmedAfterTheTimeout() {
        brokerAnswers(correlation -> { });

        assertThat(publisher.publish(STATUS)).isEqualTo(new PublishOutcome.Unconfirmed(CONFIRM_TIMEOUT));
    }

    @Test
    void failedConfirmation_isRejectedWithTheFailureType() {
        brokerAnswers(correlation -> correlation.getFuture().completeExceptionally(new IllegalStateException("канал закрыт")));

        assertThat(publisher.publish(STATUS)).isEqualTo(new PublishOutcome.Rejected(
                "ожидание подтверждения брокера завершилось ошибкой: java.lang.IllegalStateException"));
    }

    @Test
    void interruptedWait_isRejectedAndKeepsTheInterruptFlag() {
        brokerAnswers(correlation -> Thread.currentThread().interrupt());

        PublishOutcome outcome = publisher.publish(STATUS);

        assertThat(Thread.interrupted()).as("флаг прерывания восстановлен").isTrue();
        assertThat(outcome).isEqualTo(new PublishOutcome.Interrupted());
    }

    @Test
    void unreachableBroker_meansTheMessageWasNotSent() {
        willThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .given(template).send(eq(""), eq("status-changes"), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(STATUS)).isEqualTo(new PublishOutcome.NotSent(
                "брокер недоступен: org.springframework.amqp.AmqpConnectException"));
    }

    @Test
    void eventTypeWithoutQueue_isRefusedWithoutSending() {
        assertThat(publisher.publish(new OutboundMessage(MESSAGE_ID, REQUEST, "SomethingElse", "{}")))
                .isEqualTo(new PublishOutcome.Refused("для типа события SomethingElse нет очереди"));
        verifyNoInteractions(template);
    }

    @Test
    void eventTypesByBroker_areThoseOfTheRoutes() {
        assertThat(publisher.eventTypesByBroker()).containsExactlyInAnyOrder(Set.of("ContractStatusChanged"),
                Set.of("KmContractSubmitted", "LmPolicyRequested"));
    }

    private void brokerAnswers(Consumer<CorrelationData> broker) {
        willAnswer(invocation -> {
            broker.accept(invocation.getArgument(3));
            return null;
        }).given(template).send(eq(""), eq("status-changes"), any(Message.class), any(CorrelationData.class));
        given(template.isMandatoryFor(any())).willReturn(true);
    }
}
