package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.journal.OutboxProperties;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.support.TestLeadership;

class OutboxPublisherBrokersTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final Limit BATCH = Limit.of(7);
    private static final String STATUS_CHANGE = "ContractStatusChanged";
    private static final String DOCUMENT_FIX = "PaymentOrderFixed";
    private static final String POLICY_REQUEST = "LmPolicyRequested";
    private static final Set<String> MARKETING = Set.of(DOCUMENT_FIX);
    private static final Set<String> INTERNAL = Set.of(STATUS_CHANGE);
    private static final Set<String> ROUTED = Set.of(DOCUMENT_FIX, STATUS_CHANGE);

    private final OutboxMessageRepository messages = mock();
    private final OutboundPublisher broker = mock();
    private final OutboxOutcomes outcomes = mock();
    private final OutboxPublisher publisher = new OutboxPublisher(messages, broker, outcomes,
            new OutboxProperties(new OutboxProperties.Publishing(Duration.ofSeconds(5), 7), Duration.ofSeconds(10),
                    new OutboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new OutboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500)),
            TestLeadership.leader(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final OutboxMessage firstFix = message("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442", DOCUMENT_FIX);
    private final OutboxMessage secondFix = message("0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f", DOCUMENT_FIX);
    private final OutboxMessage firstStatus = message("c3f80a67-9d12-4b58-8e44-0a7b1f25e903", STATUS_CHANGE);
    private final OutboxMessage secondStatus = message("d4a91b78-0e23-4c69-9f55-1b8c2a36f014", STATUS_CHANGE);
    private final OutboxMessage unrouted = message("5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05", POLICY_REQUEST);

    @BeforeEach
    void twoBrokersCarryTheirOwnEventTypes() {
        given(broker.eventTypesByBroker()).willReturn(List.of(MARKETING, INTERNAL));
        given(messages.findDueOf(MARKETING, NOW, BATCH)).willReturn(List.of(firstFix, secondFix));
        given(messages.findDueOf(INTERNAL, NOW, BATCH)).willReturn(List.of(firstStatus, secondStatus));
        given(broker.publish(any())).willReturn(new PublishOutcome.Delivered());
    }

    @Test
    void messagesOfEachBroker_arePublishedInTheirOwnOrderBrokerAfterBroker() {
        assertThat(publisher.publishDue()).isEqualTo(4);

        InOrder order = inOrder(broker);
        order.verify(broker).publish(outbound(firstFix));
        order.verify(broker).publish(outbound(secondFix));
        order.verify(broker).publish(outbound(firstStatus));
        order.verify(broker).publish(outbound(secondStatus));
    }

    @Test
    void unreachableBroker_keepsOnlyItsOwnMessagesWaitingAndTheOtherBrokerIsStillServed() {
        given(broker.publish(outbound(firstFix))).willReturn(new PublishOutcome.NotSent("брокер недоступен"));

        assertThat(publisher.publishDue()).isEqualTo(2);

        verify(broker, never()).publish(outbound(secondFix));
        verify(outcomes, never()).record(firstFix.id(), new PublishOutcome.Delivered());
        verify(outcomes).record(firstStatus.id(), new PublishOutcome.Delivered());
        verify(outcomes).record(secondStatus.id(), new PublishOutcome.Delivered());
    }

    @Test
    void silentBroker_endsOnlyItsOwnPassAfterRecordingTheAttempt() {
        PublishOutcome.Unconfirmed unconfirmed = new PublishOutcome.Unconfirmed(Duration.ofSeconds(10));
        given(broker.publish(outbound(firstFix))).willReturn(unconfirmed);

        assertThat(publisher.publishDue()).isEqualTo(3);

        verify(outcomes).record(firstFix.id(), unconfirmed);
        verify(broker, never()).publish(outbound(secondFix));
        verify(broker).publish(outbound(firstStatus));
        verify(broker).publish(outbound(secondStatus));
    }

    @Test
    void serviceShutdownDuringTheFirstBroker_endsTheWholePassBeforeTheNextBrokerIsRead() {
        given(broker.publish(outbound(secondFix))).willReturn(new PublishOutcome.Interrupted());

        assertThat(publisher.publishDue()).isEqualTo(1);

        verify(outcomes).record(firstFix.id(), new PublishOutcome.Delivered());
        verify(messages, never()).findDueOf(INTERNAL, NOW, BATCH);
        verify(messages, never()).findDueExcept(any(), any(), any());
        verify(broker, never()).publish(outbound(firstStatus));
    }

    @Test
    void serviceShutdownDuringTheLastBroker_leavesMessagesWithoutRouteUnread() {
        given(broker.publish(outbound(firstStatus))).willReturn(new PublishOutcome.Interrupted());

        assertThat(publisher.publishDue()).isEqualTo(2);

        verify(messages, never()).findDueExcept(any(), any(), any());
    }

    @Test
    void messagesOfEventTypesWithoutRoute_areHandedToThePublisherAndTheirRefusalIsRecorded() {
        PublishOutcome.Refused refused = new PublishOutcome.Refused("для типа события LmPolicyRequested нет очереди");
        given(messages.findDueExcept(ROUTED, NOW, BATCH)).willReturn(List.of(unrouted));
        given(broker.publish(outbound(unrouted))).willReturn(refused);

        assertThat(publisher.publishDue()).isEqualTo(5);

        verify(outcomes).record(unrouted.id(), refused);
    }

    @Test
    void unreachableLastBroker_doesNotKeepMessagesWithoutRouteFromTheirRefusal() {
        PublishOutcome.Refused refused = new PublishOutcome.Refused("для типа события LmPolicyRequested нет очереди");
        given(messages.findDueExcept(ROUTED, NOW, BATCH)).willReturn(List.of(unrouted));
        given(broker.publish(outbound(firstStatus))).willReturn(new PublishOutcome.NotSent("брокер недоступен"));
        given(broker.publish(outbound(unrouted))).willReturn(refused);

        assertThat(publisher.publishDue()).isEqualTo(3);

        verify(outcomes).record(unrouted.id(), refused);
    }

    @Test
    void publisherWithoutBrokers_readsOnlyMessagesWithoutRoute() {
        given(broker.eventTypesByBroker()).willReturn(List.of());
        given(messages.findDueExcept(Set.of(), NOW, BATCH)).willReturn(List.of(unrouted));

        assertThat(publisher.publishDue()).isEqualTo(1);

        verify(messages, never()).findDueOf(any(), any(), any());
        verify(broker).publish(outbound(unrouted));
    }

    @Test
    void nothingDueAnywhere_sendsNothing() {
        given(messages.findDueOf(MARKETING, NOW, BATCH)).willReturn(List.of());
        given(messages.findDueOf(INTERNAL, NOW, BATCH)).willReturn(List.of());

        assertThat(publisher.publishDue()).isZero();

        verify(broker, never()).publish(any());
        verifyNoInteractions(outcomes);
    }

    private static OutboundMessage outbound(OutboxMessage message) {
        MessageEnvelope envelope = message.envelope();
        return new OutboundMessage(envelope.messageId(), envelope.correlationId(), envelope.eventType(), "{\"n\": 1}");
    }

    private static OutboxMessage message(String messageId, String eventType) {
        OutboxMessage message = OutboxMessage.compose(new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST,
                new MessageEnvelope(UUID.fromString(messageId), REQUEST, "rvl.contract-status.changed", eventType, 1),
                "{\"n\": 1}"), NOW);
        ReflectionTestUtils.setField(message, "id", UUID.fromString(messageId));
        return message;
    }
}
