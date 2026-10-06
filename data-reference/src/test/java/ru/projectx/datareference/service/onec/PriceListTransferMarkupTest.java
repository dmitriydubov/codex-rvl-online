package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разметку сообщения передачи прайс-листа: к какой передаче оно относится, финальное ли оно и сколько
 * в нём строк.
 *
 * <p>По этой разметке сборка находит части передачи и сверяет счётчики, не разбирая тела заново. Перепутать
 * финальное сообщение с пакетом значит никогда не собрать передачу или собрать её неполной.
 */
class PriceListTransferMarkupTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void batch_hasNoExpectedBatches_andCountsItsOwnRows() {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(node("""
                {"cid":"%s","recordSet":[{"rowNumber":1},{"rowNumber":2}]}""".formatted(CID)));

        assertThat(markup).isEqualTo(new PriceListTransferMarkup(CID, null, 2));
    }

    @Test
    void finalMessage_carriesCountersOfWholeTransfer() {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(node("""
                {"cid":"%s","countMessages":2,"countRows":3}""".formatted(CID)));

        assertThat(markup).isEqualTo(new PriceListTransferMarkup(CID, 2, 3));
    }

    /** Ноль пакетов — легальная передача, которая очищает прайс; финальное узнаётся по наличию счётчика. */
    @Test
    void finalMessageWithZeroBatches_isStillFinal() {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(node("""
                {"cid":"%s","countMessages":0,"countRows":0}""".formatted(CID)));

        assertThat(markup).isEqualTo(new PriceListTransferMarkup(CID, 0, 0));
    }

    /** Счётчик за пределом целого — нарушение контракта с именем поля, без значения. */
    @ParameterizedTest
    @CsvSource({"countMessages, 1e10, 1", "countMessages, -10000000000, 1", "countRows, 1, 2147483648"})
    void counterBeyondIntBound_isContractViolationNamingField(String field, String countMessages, String countRows) {
        ObjectNode data = node("""
                {"cid":"%s","countMessages":%s,"countRows":%s}""".formatted(CID, countMessages, countRows));

        assertThatThrownBy(() -> PriceListTransferMarkup.of(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле " + field + " больше допустимого");
    }

    /** Счётчик, пришедший не числом JSON, а строкой с целым, читается как прежде — тем же целым. */
    @Test
    void counterWrittenAsText_isReadAsBefore() {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(node("""
                {"cid":"%s","countMessages":"2","countRows":"3"}""".formatted(CID)));

        assertThat(markup).isEqualTo(new PriceListTransferMarkup(CID, 2, 3));
    }

    @Test
    void messageWithoutTransferId_isContractViolation() {
        assertThatThrownBy(() -> PriceListTransferMarkup.of(node("{\"recordSet\":[]}")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("cid");
    }

    @Test
    void markup_putsMessageIntoBuffer() {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", Instant.EPOCH), "{}");

        new PriceListTransferMarkup(CID, 2, 3).applyTo(message);

        assertThat(message.outcome().status()).isEqualTo(InboundStatus.BUFFERED);
        assertThat(message.transferPart().correlationId()).isEqualTo(CID);
        assertThat(message.transferPart().batchCount()).isEqualTo(2);
        assertThat(message.transferPart().rowCount()).isEqualTo(3);
    }

    private static ObjectNode node(String text) {
        return JSON.readValue(text, ObjectNode.class);
    }
}
