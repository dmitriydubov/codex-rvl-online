package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Хвост прежнего исхода ломает разбор незаметно: время следующей попытки вернуло бы обработанное сообщение
 * в повторы, а старая ошибка запутала бы разбор инцидента. Поэтому тесты сравнивают исход перехода целиком.
 */
class InboundMessageTransitionsTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000aa");
    private static final UUID CID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc");
    private static final String QUEUE = "1c-usk_to_rvl-online_price-list";
    private static final String PAYLOAD = "{\"data\":{}}";
    private static final Instant RECEIVED_AT = Instant.parse("2026-03-14T09:15:00Z");
    private static final Instant RETRY_AT = RECEIVED_AT.plus(Duration.ofSeconds(30));
    private static final Instant LATER_RETRY_AT = RECEIVED_AT.plus(Duration.ofMinutes(5));
    private static final Instant PROCESSED_AT = RECEIVED_AT.plus(Duration.ofMinutes(10));
    private static final String ERROR = "база недоступна";

    @Test
    void receivedMessage_isNewWithoutAttemptsAndKeepsWhatCame() {
        InboundMessage message = received();

        assertThat(message.receipt())
                .isEqualTo(new InboundReceipt(MESSAGE_ID, OneCStream.PRICE_LISTS, QUEUE, RECEIVED_AT));
        assertThat(message.payload()).isEqualTo(PAYLOAD);
        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.NEW, 0, null, null, null, null));
        assertThat(message.transferPart()).isEqualTo(new InboundTransferPart(null, null, null));
    }

    @Test
    void retrying_countsTheAttemptAndSchedulesTheNextOne() {
        InboundMessage message = received();

        message.markRetrying("первая ошибка", RETRY_AT);
        message.markRetrying(ERROR, LATER_RETRY_AT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 2, LATER_RETRY_AT, ERROR, null, null));
    }

    @Test
    void processed_clearsTheScheduledRetryAndTheLastError() {
        InboundMessage message = retriedOnce();

        message.markProcessed(PROCESSED_AT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.PROCESSED, 1, null, null, PROCESSED_AT, null));
    }

    @Test
    void skipped_keepsTheReasonInsteadOfTheLastError() {
        InboundMessage message = retriedOnce();

        message.markSkipped("услуги нет", PROCESSED_AT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.SKIPPED, 1, null, "услуги нет", PROCESSED_AT, null));
    }

    @Test
    void failed_keepsTheErrorAndCancelsTheScheduledRetry() {
        InboundMessage message = retriedOnce();

        message.markFailed("лестница исчерпана");

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.FAILED, 1, null, "лестница исчерпана", null, null));
    }

    @Test
    void bufferedBatch_remembersItsTransferAndCancelsTheScheduledRetry() {
        InboundMessage message = retriedOnce();

        message.markBuffered(CID, null, 2);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.BUFFERED, 1, null, ERROR, null, null));
        assertThat(message.transferPart()).isEqualTo(new InboundTransferPart(CID, null, 2));
    }

    @Test
    void bufferedFinalMessage_remembersTheExpectedNumberOfBatches() {
        InboundMessage message = received();

        message.markBuffered(CID, 3, 7);

        assertThat(message.transferPart()).isEqualTo(new InboundTransferPart(CID, 3, 7));
    }

    private static InboundMessage received() {
        return InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.PRICE_LISTS, QUEUE, RECEIVED_AT), PAYLOAD);
    }

    private static InboundMessage retriedOnce() {
        InboundMessage message = received();
        message.markRetrying(ERROR, RETRY_AT);
        return message;
    }
}
