package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final MessageEnvelope ENVELOPE = new MessageEnvelope(MESSAGE_ID, REQUEST,
            "rvl.contract-status.changed", "ContractStatusChanged", 1);

    private final OutboxMessage message = OutboxMessage.compose(
            new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST, ENVELOPE, "{\"data\": {}}"), NOW);

    @Test
    void composedMessage_waitsForPublicationWithItsEnvelopeAndBody() {
        assertThat(message.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(message.attempts()).isZero();
        assertThat(message.envelope()).isEqualTo(ENVELOPE);
        assertThat(message.payload()).contains("{\"data\": {}}");
        assertThat(message.errorMessage()).isEmpty();
        assertThat(message).hasFieldOrPropertyWithValue("aggregateType", "INDIVIDUAL_REQUEST")
                .hasFieldOrPropertyWithValue("aggregateId", REQUEST)
                .hasFieldOrPropertyWithValue("createdAt", NOW)
                .hasFieldOrPropertyWithValue("publishedAt", null);
    }

    @Test
    void markPublished_countsTheAttemptAndRemembersTheMoment() {
        message.markPublished(NOW.plusSeconds(5));

        assertThat(message.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message).hasFieldOrPropertyWithValue("publishedAt", NOW.plusSeconds(5))
                .hasFieldOrPropertyWithValue("nextAttemptAt", null);
    }

    @Test
    void markRetry_countsTheAttemptAndSchedulesTheNextOne() {
        message.markRetry("подтверждение не получено", NOW.plusSeconds(60));

        assertThat(message.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains("подтверждение не получено");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", NOW.plusSeconds(60));
    }

    @Test
    void markFailed_afterRetries_keepsTheCountAndClearsTheSchedule() {
        message.markRetry("отказ", NOW.plusSeconds(60));
        message.markFailed("повторы исчерпаны");

        assertThat(message.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.attempts()).isEqualTo(2);
        assertThat(message.errorMessage()).contains("повторы исчерпаны");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", null);
    }

    @Test
    void finishedMessage_acceptsNoFurtherOutcome() {
        message.markPublished(NOW);

        assertThatThrownBy(() -> message.markFailed("отказ")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MESSAGE_ID.toString()).hasMessageContaining("PUBLISHED");
        assertThatThrownBy(() -> message.markRetry("отказ", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> message.markPublished(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(message.attempts()).isEqualTo(1);
    }
}
