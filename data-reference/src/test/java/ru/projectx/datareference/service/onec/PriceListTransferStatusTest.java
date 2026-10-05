package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Защищает решение, во что сложилась передача прайс-листа, и общий исход для всех её сообщений.
 *
 * <p>Прайс по услуге приходит несколькими пакетами строк и одним финальным сообщением со счётчиками.
 * Ошибка здесь стоит дорого: передача, признанная собранной раньше времени, заменит прайс неполным,
 * а сообщение, которому не проставили исход, навсегда останется в буфере. Логика чистая, поэтому
 * проверяется без базы и без контекста Spring.
 */
class PriceListTransferStatusTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final Instant RECEIVED_AT = Instant.parse("2026-03-14T09:00:00Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-03-14T09:15:00Z");

    @Test
    void batchesWithoutFinalMessage_waitForIt() {
        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(batch(2)));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Pending(
                "передача " + CID + " ждёт финального сообщения"));
    }

    @Test
    void fewerBatchesThanPromised_waitForTheRest() {
        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(finalMessage(2, 3), batch(1)));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Pending(
                "передача " + CID + " собрана на 1 из 2 сообщений"));
    }

    /** Финальное может прийти раньше последнего пакета: собранность проверяется по составу, а не по порядку. */
    @Test
    void allPromisedBatches_assembleTransfer_whateverTheOrder() {
        InboundMessage first = batch(1);
        InboundMessage second = batch(2);
        InboundMessage finalMessage = finalMessage(2, 3);

        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(first, finalMessage, second));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Assembled(finalMessage, List.of(first, second)));
    }

    /** Ноль пакетов — легальная передача: прайс по услуге очищается. */
    @Test
    void emptyTransfer_isAssembledRightAway() {
        InboundMessage finalMessage = finalMessage(0, 0);

        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(finalMessage));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Assembled(finalMessage, List.of()));
    }

    /** Сообщения той же передачи с уже проставленным исходом в сборку не входят. */
    @Test
    void messagesNoLongerBuffered_areLeftOut() {
        InboundMessage processedFinal = finalMessage(1, 1);
        processedFinal.markProcessed(PROCESSED_AT);
        InboundMessage processedBatch = batch(1);
        processedBatch.markProcessed(PROCESSED_AT);

        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(processedFinal, processedBatch));

        assertThat(transfer).isInstanceOf(PriceListTransfer.Pending.class);
    }

    /** Исход отклонённой передачи уже записан: части, пришедшие после него, не собираются заново, а отклоняются. */
    @Test
    void partsArrivingAfterRejection_areRejectedRightAway() {
        InboundMessage rejectedBatch = batch(1);
        rejectedBatch.markFailed("в строке прайса нет цены");
        InboundMessage lateFinal = finalMessage(1, 1);
        InboundMessage lateBatch = batch(1);

        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(rejectedBatch, lateFinal, lateBatch));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Rejected(
                "передача " + CID + " уже отклонена", List.of(lateFinal, lateBatch)));
    }

    /** У применённой передачи отказ бывает только у лишнего пакета: сама передача не отклонена. */
    @Test
    void appliedTransferWithRejectedSurplusBatch_isNotRejected() {
        InboundMessage applied = batch(1);
        applied.markProcessed(PROCESSED_AT);
        InboundMessage surplus = batch(1);
        surplus.markFailed("лишний пакет");

        assertThat(PriceListTransfer.isRejected(List.of(applied, surplus))).isFalse();
        assertThat(PriceListTransfer.isRejected(List.of(surplus))).isTrue();
        assertThat(PriceListTransfer.isRejected(List.of(applied))).isFalse();
        assertThat(PriceListTransfer.of(CID, List.of(applied, surplus, batch(1))))
                .isInstanceOf(PriceListTransfer.Pending.class);
    }

    @Test
    void rejectedTransfer_failsEveryLatePartWithReason() {
        InboundMessage lateFinal = finalMessage(1, 1);
        InboundMessage lateBatch = batch(1);
        PriceListTransfer.Rejected transfer = new PriceListTransfer.Rejected("уже отклонена", List.of(lateFinal, lateBatch));

        List<InboundMessage> failed = transfer.fail();

        assertThat(failed).containsExactly(lateFinal, lateBatch);
        assertThat(failed).allSatisfy(message -> {
            assertThat(message.outcome().status()).isEqualTo(InboundStatus.FAILED);
            assertThat(message.outcome().lastError()).isEqualTo("уже отклонена");
        });
    }

    @Test
    void morePromisedBatchesThanArrived_failWholeTransfer() {
        InboundMessage finalMessage = finalMessage(1, 2);
        InboundMessage first = batch(1);
        InboundMessage second = batch(1);

        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(finalMessage, first, second));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Mismatched(
                "счётчики передачи " + CID + " не сошлись: ожидалось 1 сообщений / 2 строк, получено 2 / 2",
                finalMessage, List.of(first, second)));
    }

    @Test
    void rowsDifferingFromPromised_failWholeTransfer() {
        InboundMessage finalMessage = finalMessage(2, 5);
        InboundMessage first = batch(1);
        InboundMessage second = batch(2);

        PriceListTransfer transfer = PriceListTransfer.of(CID, List.of(first, second, finalMessage));

        assertThat(transfer).isEqualTo(new PriceListTransfer.Mismatched(
                "счётчики передачи " + CID + " не сошлись: ожидалось 2 сообщений / 5 строк, получено 2 / 3",
                finalMessage, List.of(first, second)));
    }

    @Test
    void mismatchedTransfer_failsEveryMessageWithReason_finalMessageFirst() {
        InboundMessage finalMessage = finalMessage(1, 5);
        InboundMessage batch = batch(1);
        PriceListTransfer.Mismatched transfer = new PriceListTransfer.Mismatched("счётчики", finalMessage, List.of(batch));

        List<InboundMessage> failed = transfer.fail();

        assertThat(failed).containsExactly(finalMessage, batch);
        assertThat(failed).allSatisfy(message -> {
            assertThat(message.outcome().status()).isEqualTo(InboundStatus.FAILED);
            assertThat(message.outcome().lastError()).isEqualTo("счётчики");
        });
    }

    @Test
    void assembledTransfer_marksEveryMessageProcessed_finalMessageFirst() {
        InboundMessage finalMessage = finalMessage(1, 1);
        InboundMessage batch = batch(1);
        PriceListTransfer.Assembled transfer = new PriceListTransfer.Assembled(finalMessage, List.of(batch));

        List<InboundMessage> processed = transfer.markProcessed(PROCESSED_AT);

        assertThat(processed).containsExactly(finalMessage, batch);
        assertThat(processed).allSatisfy(message -> {
            assertThat(message.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
            assertThat(message.outcome().processedAt()).isEqualTo(PROCESSED_AT);
        });
    }

    @Test
    void outcomeOfApplying_isTheOutcomeOfEveryMessage() {
        InboundMessage finalMessage = finalMessage(1, 1);
        InboundMessage batch = batch(1);
        InboundMessage skippedFinal = finalMessage(1, 1);
        InboundMessage skippedBatch = batch(1);

        List<InboundMessage> applied = new PriceListTransfer.Assembled(finalMessage, List.of(batch))
                .markOutcome(ImportResult.applied(), PROCESSED_AT);
        List<InboundMessage> skipped = new PriceListTransfer.Assembled(skippedFinal, List.of(skippedBatch))
                .markOutcome(ImportResult.skipped("услуги нет"), PROCESSED_AT);

        assertThat(applied).containsExactly(finalMessage, batch)
                .allSatisfy(message -> assertThat(message.outcome().status()).isEqualTo(InboundStatus.PROCESSED));
        assertThat(skipped).containsExactly(skippedFinal, skippedBatch).allSatisfy(message -> {
            assertThat(message.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
            assertThat(message.outcome().lastError()).isEqualTo("услуги нет");
            assertThat(message.outcome().processedAt()).isEqualTo(PROCESSED_AT);
        });
    }

    @Test
    void assembledTransfer_skipsEveryMessageWithReason_finalMessageFirst() {
        InboundMessage finalMessage = finalMessage(1, 1);
        InboundMessage batch = batch(1);
        PriceListTransfer.Assembled transfer = new PriceListTransfer.Assembled(finalMessage, List.of(batch));

        List<InboundMessage> skipped = transfer.markSkipped("услуги нет", PROCESSED_AT);

        assertThat(skipped).containsExactly(finalMessage, batch);
        assertThat(skipped).allSatisfy(message -> {
            assertThat(message.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
            assertThat(message.outcome().lastError()).isEqualTo("услуги нет");
            assertThat(message.outcome().processedAt()).isEqualTo(PROCESSED_AT);
        });
    }

    private static InboundMessage batch(int rows) {
        InboundMessage message = received();
        message.markBuffered(CID, null, rows);
        return message;
    }

    private static InboundMessage finalMessage(int batchCount, int rows) {
        InboundMessage message = received();
        message.markBuffered(CID, batchCount, rows);
        return message;
    }

    private static InboundMessage received() {
        return InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", RECEIVED_AT), "{}");
    }
}
