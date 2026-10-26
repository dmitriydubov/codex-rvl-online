package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.OutboxProperties;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.repository.OutboxMessageRepository;

class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final String FIRST = "7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442";
    private static final String SECOND = "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f";

    private final OutboxMessageRepository messages = mock();
    private final OutboundBodies bodies = mock();
    private final OutboundPublisher broker = mock();
    private final OutboxOutcomes outcomes = mock();
    private final OutboxPublisher publisher = new OutboxPublisher(messages, bodies, broker, outcomes,
            new OutboxProperties(new OutboxProperties.Publishing(Duration.ofSeconds(5), 7), Duration.ofSeconds(10),
                    new OutboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new OutboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500)),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final OutboxMessage first = message(FIRST, "{\"n\": 1}");
    private final OutboxMessage second = message(SECOND, "{\"n\": 2}");

    @BeforeEach
    void bodiesAreCompleteAsStored() {
        given(bodies.complete(any(), any())).willAnswer(call -> new OutboundBody.Complete(call.getArgument(1)));
    }

    @Test
    void dueMessages_arePublishedInOrderAndEachOutcomeIsRecordedBeforeTheNextSend() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        given(broker.publish(any())).willReturn(new PublishOutcome.Delivered());

        assertThat(publisher.publishDue()).isEqualTo(2);

        InOrder order = inOrder(broker, outcomes);
        order.verify(broker).publish(new OutboundMessage(UUID.fromString(FIRST), REQUEST, "ContractStatusChanged",
                "{\"n\": 1}"));
        order.verify(outcomes).record(first.id(), new PublishOutcome.Delivered());
        order.verify(broker).publish(new OutboundMessage(UUID.fromString(SECOND), REQUEST, "ContractStatusChanged",
                "{\"n\": 2}"));
        order.verify(outcomes).record(second.id(), new PublishOutcome.Delivered());
    }

    @Test
    void refusedMessage_isRecordedAndTheOthersAreStillSent() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        PublishOutcome.Refused refused = new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)");
        given(broker.publish(any())).willReturn(refused, new PublishOutcome.Delivered());

        assertThat(publisher.publishDue()).isEqualTo(2);

        verify(outcomes).record(first.id(), refused);
        verify(outcomes).record(second.id(), new PublishOutcome.Delivered());
    }

    @Test
    void unreachableBroker_endsThePassWithoutRecordingAnAttempt() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        given(broker.publish(any())).willReturn(new PublishOutcome.NotSent("брокер недоступен"));

        assertThat(publisher.publishDue()).isZero();

        verify(broker).publish(any());
        verifyNoInteractions(outcomes);
    }

    @Test
    void interruptedWait_endsThePassWithoutRecordingAnAttempt() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        given(broker.publish(any())).willReturn(new PublishOutcome.Interrupted());

        assertThat(publisher.publishDue()).isZero();

        verify(broker).publish(any());
        verifyNoInteractions(outcomes);
    }

    @Test
    void unconfirmedMessage_isRecordedAndEndsThePass() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        PublishOutcome.Unconfirmed unconfirmed = new PublishOutcome.Unconfirmed(Duration.ofSeconds(10));
        given(broker.publish(any())).willReturn(unconfirmed);

        assertThat(publisher.publishDue()).isEqualTo(1);

        verify(outcomes).record(first.id(), unconfirmed);
        verify(outcomes, never()).record(second.id(), unconfirmed);
    }

    @Test
    void bodyWithAttachments_isSentAsCompletedAndNotAsStored() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first));
        given(bodies.complete(first.id(), "{\"n\": 1}")).willReturn(new OutboundBody.Complete("{\"n\": 1, \"c\": \"x\"}"));
        given(broker.publish(any())).willReturn(new PublishOutcome.Delivered());

        assertThat(publisher.publishDue()).isEqualTo(1);

        verify(broker).publish(new OutboundMessage(UUID.fromString(FIRST), REQUEST, "ContractStatusChanged",
                "{\"n\": 1, \"c\": \"x\"}"));
    }

    @Test
    void withheldBody_isNotSentAndItsOutcomeIsRecorded() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        PublishOutcome.Refused refused = new PublishOutcome.Refused("объекта справки нет в хранилище");
        given(bodies.complete(first.id(), "{\"n\": 1}")).willReturn(new OutboundBody.Withheld(refused));
        given(broker.publish(any())).willReturn(new PublishOutcome.Delivered());

        assertThat(publisher.publishDue()).isEqualTo(2);

        verify(outcomes).record(first.id(), refused);
        verify(broker).publish(new OutboundMessage(UUID.fromString(SECOND), REQUEST, "ContractStatusChanged",
                "{\"n\": 2}"));
        verify(outcomes).record(second.id(), new PublishOutcome.Delivered());
    }

    @Test
    void bodyWithheldByAnUnavailableStorage_endsThePassWithoutAnAttempt() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        given(bodies.complete(first.id(), "{\"n\": 1}"))
                .willReturn(new OutboundBody.Withheld(new PublishOutcome.NotSent("хранилище справок недоступно")));

        assertThat(publisher.publishDue()).isZero();

        verifyNoInteractions(broker, outcomes);
        verify(bodies, never()).complete(second.id(), "{\"n\": 2}");
    }

    @Test
    void messageWithoutBody_isRefusedWithoutSending() {
        OutboxMessage erased = message(FIRST, null);
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(erased));

        assertThat(publisher.publishDue()).isEqualTo(1);

        verifyNoInteractions(broker, bodies);
        verify(outcomes).record(erased.id(), new PublishOutcome.Refused("тело сообщения стёрто по сроку хранения"));
    }

    @Test
    void failedOutcomeRecording_endsThePassWithTheFailureBeforeTheNextMessage() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        PublishOutcome.Refused refused = new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)");
        given(broker.publish(any())).willReturn(refused);
        CannotAcquireLockException busy = new CannotAcquireLockException("строка полиса занята");
        willThrow(busy).given(outcomes).record(first.id(), refused);

        assertThatThrownBy(publisher::publishDue).isSameAs(busy);

        verify(broker).publish(any());
        verify(outcomes, never()).record(second.id(), refused);
    }

    @Test
    void nothingDue_sendsNothing() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of());

        assertThat(publisher.publishDue()).isZero();
        verifyNoInteractions(broker, outcomes);
    }

    private static OutboxMessage message(String messageId, String payload) {
        OutboxMessage message = OutboxMessage.compose(new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST,
                new MessageEnvelope(UUID.fromString(messageId), REQUEST, "rvl.contract-status.changed",
                        "ContractStatusChanged", 1), payload), NOW);
        ReflectionTestUtils.setField(message, "id", UUID.fromString(messageId));
        return message;
    }
}
