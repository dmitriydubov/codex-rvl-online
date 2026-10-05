package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает сборку прайс-листа, который приходит частями: каждое сообщение откладывается в буфер с разметкой
 * передачи, а прайс применяется только когда передача собралась целиком.
 *
 * <p>Здесь проверяется то, что сборщик делает сам: какую разметку получает отложенное сообщение, какой набор
 * строк уходит в применение прайса и что сохраняется после исхода. Само решение о готовности передачи
 * проверяет {@link PriceListTransferStatusTest}, а путь целиком на настоящей базе — {@link PriceListTransferTest}.
 */
@ExtendWith(MockitoExtension.class)
class PriceListTransferAssemblerTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final UUID SERVICE_ID = UUID.fromString("8e4b0f70-0000-4000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private InboundMessageRepository repository;

    @Mock
    private PriceListMapper priceListMapper;

    private final List<InboundMessage> correlated = new ArrayList<>();

    @Test
    void handlesPriceListStream() {
        assertThat(assembler().stream()).isEqualTo(OneCStream.PRICE_LISTS);
    }

    /** Пакет строк — не финальное сообщение: ожидаемого числа пакетов у него нет, а строк — сколько в нём пришло. */
    @Test
    void batch_isBufferedWithItsRowCount_andWaitsForFinalMessage() {
        InboundMessage batch = message(batchData(row(1, "100.00"), row(2, "200.00")));

        ImportResult result = receive(batch);

        assertThat(result).isEqualTo(ImportResult.skipped("передача " + CID + " ждёт финального сообщения"));
        assertThat(batch.status()).isEqualTo(InboundStatus.BUFFERED);
        assertThat(batch.correlationId()).isEqualTo(CID);
        assertThat(batch.batchCount()).isNull();
        assertThat(batch.rowCount()).isEqualTo(2);
        verify(repository).save(batch);
        verify(priceListMapper, never()).apply(any());
    }

    /** Финальное сообщение узнаётся по наличию счётчиков, а не по их значению: ноль пакетов тоже легален. */
    @Test
    void finalMessage_isBufferedWithItsCounters() {
        InboundMessage finalMessage = message(finalData(2, 3));

        ImportResult result = receive(finalMessage);

        assertThat(result).isEqualTo(ImportResult.skipped("передача " + CID + " собрана на 0 из 2 сообщений"));
        assertThat(finalMessage.status()).isEqualTo(InboundStatus.BUFFERED);
        assertThat(finalMessage.correlationId()).isEqualTo(CID);
        assertThat(finalMessage.batchCount()).isEqualTo(2);
        assertThat(finalMessage.rowCount()).isEqualTo(3);
    }

    /** Строки всех пакетов идут в применение одним набором по возрастанию номера, как их пронумеровал источник. */
    @Test
    void assembledTransfer_appliesRowsOfAllBatchesInSourceOrder_andMarksWholeTransferProcessed() {
        InboundMessage first = buffered(batchData(row(3, "300.00"), row(1, "100.00")), null, 2);
        InboundMessage second = buffered(batchData(row(2, "200.00")), null, 1);
        InboundMessage finalMessage = message(finalData(2, 3));
        when(priceListMapper.apply(any())).thenReturn(ImportResult.applied());

        ImportResult result = receive(finalMessage);

        assertThat(result).isEqualTo(ImportResult.applied());
        verify(priceListMapper).apply(node("""
                {"keyValue":"%s","recordSet":[%s,%s,%s]}"""
                .formatted(SERVICE_ID, row(1, "100.00"), row(2, "200.00"), row(3, "300.00"))));
        assertThat(List.of(finalMessage, first, second)).allSatisfy(message -> {
            assertThat(message.status()).isEqualTo(InboundStatus.PROCESSED);
            assertThat(message.processedAt()).isEqualTo(NOW);
        });
        verifySavedAfterBuffering(finalMessage, first, second);
    }

    @Test
    void transferSkippedByPriceList_isSkippedWholeWithThatReason() {
        InboundMessage batch = buffered(batchData(row(1, "100.00")), null, 1);
        InboundMessage finalMessage = message(finalData(1, 1));
        when(priceListMapper.apply(any())).thenReturn(ImportResult.skipped("услуги нет"));

        ImportResult result = receive(finalMessage);

        assertThat(result).isEqualTo(ImportResult.skipped("услуги нет"));
        assertThat(List.of(finalMessage, batch)).allSatisfy(message -> {
            assertThat(message.status()).isEqualTo(InboundStatus.SKIPPED);
            assertThat(message.lastError()).isEqualTo("услуги нет");
            assertThat(message.processedAt()).isEqualTo(NOW);
        });
        verifySavedAfterBuffering(finalMessage, batch);
    }

    /** Счётчики не сошлись — вся передача в ошибку, а прайс не применяется вовсе. */
    @Test
    void mismatchedCounters_failWholeTransfer_withoutTouchingPriceList() {
        InboundMessage batch = buffered(batchData(row(1, "100.00")), null, 1);
        InboundMessage finalMessage = message(finalData(1, 5));
        String reason = "счётчики передачи " + CID + " не сошлись: ожидалось 1 сообщений / 5 строк, получено 1 / 1";

        ImportResult result = receive(finalMessage);

        assertThat(result).isEqualTo(ImportResult.skipped(reason));
        assertThat(List.of(finalMessage, batch)).allSatisfy(message -> {
            assertThat(message.status()).isEqualTo(InboundStatus.FAILED);
            assertThat(message.lastError()).isEqualTo(reason);
        });
        verifySavedAfterBuffering(finalMessage, batch);
        verify(priceListMapper, never()).apply(any());
    }

    /** Тело пакета стёрто — собрать передачу нечем, и повтор этого не исправит. */
    @Test
    void batchWithErasedBody_isContractViolation() {
        InboundMessage batch = buffered(batchData(row(1, "100.00")), null, 1);
        batch.forgetPayload();
        InboundMessage finalMessage = message(finalData(1, 1));

        assertThatThrownBy(() -> receive(finalMessage))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("тело сообщения передачи стёрто, собрать её нечем");
    }

    @Test
    void messageWithoutTransferId_isContractViolation() {
        InboundMessage message = message("{\"keyValue\":\"%s\",\"recordSet\":[]}".formatted(SERVICE_ID));

        assertThatThrownBy(() -> assembler().handle(message, dataOf(message)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("cid");
    }

    private ImportResult receive(InboundMessage message) {
        correlated.add(message);
        when(repository.findByCorrelationId(CID)).thenReturn(correlated);
        return assembler().handle(message, dataOf(message));
    }

    /** Сначала сохраняется разметка пришедшего сообщения, затем исход всей передачи: финальное первым. */
    private void verifySavedAfterBuffering(InboundMessage received, InboundMessage... others) {
        InOrder saves = inOrder(repository);
        saves.verify(repository).save(received);
        saves.verify(repository).findByCorrelationId(CID);
        saves.verify(repository).save(received);
        for (InboundMessage other : others) {
            saves.verify(repository).save(other);
        }
    }

    private InboundMessage buffered(String data, Integer batchCount, int rowCount) {
        InboundMessage message = message(data);
        message.markBuffered(CID, batchCount, rowCount);
        correlated.add(message);
        return message;
    }

    private static InboundMessage message(String data) {
        return InboundMessage.receive(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", envelope(data), NOW);
    }

    private ObjectNode dataOf(InboundMessage message) {
        return (ObjectNode) json.readTree(message.payload()).get("data");
    }

    private ObjectNode node(String text) {
        return json.readValue(text, ObjectNode.class);
    }

    private PriceListTransferAssembler assembler() {
        return new PriceListTransferAssembler(repository, priceListMapper, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String batchData(String... rows) {
        return "{\"cid\":\"%s\",\"keyValue\":\"%s\",\"recordSet\":[%s]}"
                .formatted(CID, SERVICE_ID, String.join(",", rows));
    }

    private static String finalData(int countMessages, int countRows) {
        return "{\"cid\":\"%s\",\"keyValue\":\"%s\",\"countMessages\":%d,\"countRows\":%d}"
                .formatted(CID, SERVICE_ID, countMessages, countRows);
    }

    private static String row(int rowNumber, String price) {
        return "{\"rowNumber\":%d,\"price\":%s}".formatted(rowNumber, price);
    }
}
