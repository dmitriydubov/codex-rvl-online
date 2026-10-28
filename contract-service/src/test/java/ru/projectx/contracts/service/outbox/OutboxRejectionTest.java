package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.entity.outbox.OutboxStatus;

class OutboxRejectionTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID POLICY = UUID.fromString("01960000-0000-7000-8000-0000000000aa");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");

    private final OutboxMetrics metrics = mock();
    private final RejectedOutboxMessages policies = mock();
    private final RejectedOutboxMessages contracts = mock();

    @BeforeEach
    void reactionsKnowTheirAggregates() {
        given(policies.aggregateType()).willReturn("LM_POLICY");
        given(contracts.aggregateType()).willReturn("KM_CONTRACT");
    }

    @Test
    void rejection_marksTheMessageFailedAndCountsItByEventType() {
        OutboxMessage message = message("LM_POLICY", "LmPolicyRequested");

        new OutboxRejection(metrics, List.of(policies, contracts)).reject(message, "отказ", NOW);

        assertThat(message.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.errorMessage()).contains("отказ");
        verify(metrics).countFailed("LmPolicyRequested");
    }

    @Test
    void rejection_tellsTheReactionOfItsAggregateTypeOnly() {
        new OutboxRejection(metrics, List.of(policies, contracts))
                .reject(message("LM_POLICY", "LmPolicyRequested"), "отказ", NOW);

        verify(policies).rejected(POLICY, NOW);
        verify(contracts, never()).rejected(any(), any());
    }

    @Test
    void aggregateWithoutReaction_isOnlyMarkedFailed() {
        OutboxMessage message = message("INDIVIDUAL_REQUEST", "ContractStatusChanged");

        new OutboxRejection(metrics, List.of(policies, contracts)).reject(message, "отказ", NOW);

        assertThat(message.status()).isEqualTo(OutboxStatus.FAILED);
        verify(policies, never()).rejected(any(), any());
        verify(contracts, never()).rejected(any(), any());
    }

    @Test
    void twoReactionsForOneAggregateType_failAtStartup() {
        given(contracts.aggregateType()).willReturn("LM_POLICY");

        assertThatThrownBy(() -> new OutboxRejection(metrics, List.of(policies, contracts)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static OutboxMessage message(String aggregateType, String eventType) {
        MessageEnvelope envelope = new MessageEnvelope(UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442"),
                REQUEST, "topic", eventType, 2);
        UUID aggregateId = "LM_POLICY".equals(aggregateType) ? POLICY : REQUEST;
        return OutboxMessage.compose(new OutboxDraft(aggregateType, aggregateId, envelope, "{}"), NOW.minusSeconds(5));
    }
}
