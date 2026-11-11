package ru.projectx.contracts.service.integration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.receipt.InboxReceiver;
import ru.projectx.contracts.service.inbox.receipt.ReceiptOutcome;
import ru.projectx.contracts.support.TaskBodies;
import tools.jackson.databind.json.JsonMapper;

class InboxQueueListenerReadingTest {

    private static final String EVENT = "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\"}";
    private static final InboxReceipt EVENT_RECEIPT = new InboxReceipt(
            UUID.fromString("d1a0c001-5e2f-4a3b-8c4d-000000000001"),
            UUID.fromString("0196f0a2-0000-7000-8000-000000000011"), "ordering.order.events", "PaymentOrderEvent", 0);
    private static final InboxReceipt TASK_RECEIPT = new InboxReceipt(UUID.fromString(TaskBodies.MESSAGE_ID),
            UUID.fromString(TaskBodies.REQUEST_ID), "rvl.contract-task.upserted", "ContractTaskUpserted", 1);

    private final InboxReceiver receiver = mock();
    private final Channel channel = mock();
    private final InboxStorageFailures failures = mock();
    private final InboxMetrics metrics = mock();
    private final InboxEnvelopes orderEvents = mock();
    private final InboxQueueListener taskListener = new InboxQueueListener(
            new TaskEnvelopeParser(JsonMapper.builder().build()), receiver, failures, metrics);
    private final InboxQueueListener eventListener = taskListener.reading(orderEvents);

    @Test
    void listenerReadingOtherEnvelopes_storesTheMessageUnderTheirReceiptAndAcknowledgesIt() throws IOException {
        given(orderEvents.parse(any())).willReturn(new TaskEnvelope.Storable(EVENT_RECEIPT));
        given(receiver.receive(EVENT_RECEIPT, EVENT)).willReturn(ReceiptOutcome.STORED);

        eventListener.onMessage(message(EVENT), channel);

        verify(orderEvents).parse(EVENT.getBytes(StandardCharsets.UTF_8));
        verify(receiver).receive(EVENT_RECEIPT, EVENT);
        verify(channel).basicAck(7L, false);
        verifyNoInteractions(failures, metrics);
    }

    @Test
    void listenerReadingOtherEnvelopes_isAnotherListenerAndLeavesTheFirstOneReadingTasks() throws IOException {
        String task = TaskBodies.upserted().toJson();
        given(receiver.receive(TASK_RECEIPT, task)).willReturn(ReceiptOutcome.STORED);

        taskListener.onMessage(message(task), channel);

        assertThat(eventListener).isNotSameAs(taskListener);
        verify(receiver).receive(TASK_RECEIPT, task);
        verifyNoInteractions(orderEvents);
    }

    @Test
    void envelopeRefusedByTheOtherReader_isLostCountedByItsReasonAndAcknowledged() throws IOException {
        given(orderEvents.parse(any()))
                .willThrow(new InboxEnvelopeException("в событии нет eventId", InboxEnvelopeException.UNUSABLE_ENVELOPE));

        eventListener.onMessage(message("{}"), channel);

        verify(metrics).countLost("unusable_envelope");
        verify(channel).basicAck(7L, false);
        verify(receiver, never()).receive(any(), anyString());
    }

    @Test
    void unstorableEnvelopeOfTheOtherReader_goesToQuarantineThroughTheSameFailureRules() throws IOException {
        given(orderEvents.parse(any())).willReturn(new TaskEnvelope.Unstorable(EVENT_RECEIPT));

        eventListener.onMessage(message(EVENT), channel);

        verify(failures).quarantine(any(), eq(EVENT_RECEIPT),
                eq("в теле символ, который база не хранит: нулевой или непарный суррогат"));
        verify(receiver, never()).receive(any(), anyString());
    }

    @Test
    void storageFailureOfAnOrderEvent_isHandedToTheSameFailureRulesWithoutTheRequestKeyInTheLogContext()
            throws IOException {
        IllegalStateException failure = new IllegalStateException("база недоступна");
        given(orderEvents.parse(any())).willReturn(new TaskEnvelope.Storable(EVENT_RECEIPT));
        given(receiver.receive(EVENT_RECEIPT, EVENT)).willAnswer(invocation -> {
            assertThat(MDC.get("messageId")).isEqualTo(EVENT_RECEIPT.messageId().toString());
            assertThat(MDC.get("individualRequestId")).isNull();
            throw failure;
        });

        eventListener.onMessage(message(EVENT), channel);

        verify(failures).handle(any(), eq(EVENT_RECEIPT), eq(failure));
        verify(channel, never()).basicAck(7L, false);
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("order.event.rvl.topic");
        properties.setDeliveryTag(7L);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
