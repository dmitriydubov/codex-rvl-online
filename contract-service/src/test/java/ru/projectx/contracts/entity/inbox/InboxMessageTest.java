package ru.projectx.contracts.entity.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxMessageTest {

    private static final Instant RECEIVED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
            UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
            "rvl.contract-task.upserted", "ContractTaskUpserted", 3);

    private final InboxMessage message = InboxMessage.receive(RECEIPT, "{\"meta\":{}}", RECEIVED);

    @Test
    void receive_storesEnvelopeAndBodyAsNewWithoutAttempts() {
        assertThat(message.receipt()).isEqualTo(RECEIPT);
        assertThat(message.payload()).contains("{\"meta\":{}}");
        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        assertThat(message.attempts()).isZero();
        assertThat(message.errorMessage()).isEmpty();
        assertThat(message.id()).as("идентификатор присваивает база при сохранении").isNull();
        assertThat(message).hasFieldOrPropertyWithValue("receivedAt", RECEIVED)
                .hasFieldOrPropertyWithValue("processedAt", null)
                .hasFieldOrPropertyWithValue("nextAttemptAt", null);
    }

    @Test
    void finishedMessage_refusesEveryFurtherTransition() {
        message.markProcessed(LATER);

        assertThatThrownBy(() -> message.markFailed("отказ")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSED");
        assertThatThrownBy(() -> message.markRetry("отказ", LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> message.markSkipped("пропуск", LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> message.markProcessed(LATER)).isInstanceOf(IllegalStateException.class);
        assertThat(message.attempts()).isZero();
    }

    @Test
    void markProcessed_setsProcessedAtAndClearsNextAttempt() {
        message.markRetry("временный отказ", LATER);

        message.markProcessed(LATER);

        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(message).hasFieldOrPropertyWithValue("processedAt", LATER)
                .hasFieldOrPropertyWithValue("nextAttemptAt", null);
        assertThat(message.attempts()).isEqualTo(1);
    }

    @Test
    void markSkipped_keepsReasonAndProcessedAt() {
        message.markSkipped("версия объекта 1 не выше обработанной 2", LATER);

        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(message.errorMessage()).contains("версия объекта 1 не выше обработанной 2");
        assertThat(message).hasFieldOrPropertyWithValue("processedAt", LATER)
                .hasFieldOrPropertyWithValue("nextAttemptAt", null);
    }

    @Test
    void markRetry_countsAttemptAndSchedulesNextOne() {
        message.markRetry("временный отказ базы данных, SQLSTATE 55P03", LATER);

        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains("временный отказ базы данных, SQLSTATE 55P03");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", LATER);
    }

    @Test
    void markFailed_countsAttemptAndStopsRetries() {
        message.markRetry("временный отказ", LATER);

        message.markFailed("повторы исчерпаны на попытке 2: временный отказ");

        assertThat(message.status()).isEqualTo(InboxStatus.FAILED);
        assertThat(message.attempts()).isEqualTo(2);
        assertThat(message.errorMessage()).contains("повторы исчерпаны на попытке 2: временный отказ");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", null);
    }
}
