package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает то, что отклоняет одну часть передачи прайса, ещё до сборки: строку без номера, цены или даты и
 * пакет, пришедший к уже применённой передаче. Такая часть отклоняется своей причиной, остальные — ссылкой на неё.
 */
class PriceListPartCheckTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PriceListMapper priceList = mock(PriceListMapper.class);
    private final PriceListPartCheck check = new PriceListPartCheck(priceList);

    @Test
    void batchWithNumberedRows_passes_andEveryRowIsCheckedByPriceListRules() {
        ObjectNode data = batch("{\"rowNumber\":1}", "{\"rowNumber\":2}");

        check.check(markupOf(data), data, List.of());

        InOrder rows = Mockito.inOrder(priceList);
        rows.verify(priceList).checkRow(data.path("recordSet").get(0));
        rows.verify(priceList).checkRow(data.path("recordSet").get(1));
    }

    @Test
    void rowWithoutNumber_rejectsThePart_beforePriceListRules() {
        ObjectNode data = batch("{\"price\":1}");

        assertThatThrownBy(() -> check.check(markupOf(data), data, List.of()))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля rowNumber");
        verify(priceList, never()).checkRow(data.path("recordSet").get(0));
    }

    @Test
    void rowBreakingPriceListRules_rejectsThePart() {
        ObjectNode data = batch("{\"rowNumber\":1}");
        Mockito.doThrow(new OneCFieldException("в строке прайса нет цены"))
                .when(priceList).checkRow(data.path("recordSet").get(0));

        assertThatThrownBy(() -> check.check(markupOf(data), data, List.of()))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в строке прайса нет цены");
    }

    /** Применённая передача уже получила все обещанные пакеты: ещё один — лишний. */
    @Test
    void batchOfAppliedTransfer_isSurplus() {
        ObjectNode data = batch("{\"rowNumber\":1}");

        assertThatThrownBy(() -> check.check(markupOf(data), data, List.of(part(true), processedPart())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("лишний пакет: передача " + CID + " уже применена");
    }

    @Test
    void batchOfTransferStillInBuffer_isNotSurplus() {
        ObjectNode data = batch("{\"rowNumber\":1}");

        assertThatCode(() -> check.check(markupOf(data), data, List.of(part(true), part(false))))
                .doesNotThrowAnyException();
    }

    /** У финального сообщения строк нет; второе финальное сообщение — не забота этой проверки. */
    @Test
    void finalMessage_isNotChecked() {
        ObjectNode data = JSON.createObjectNode().put("cid", CID.toString()).put("countMessages", 1).put("countRows", 1);

        assertThatCode(() -> check.check(markupOf(data), data, List.of(processedPart())))
                .doesNotThrowAnyException();
        verifyNoInteractions(priceList);
    }

    private static PriceListTransferMarkup markupOf(ObjectNode data) {
        return PriceListTransferMarkup.of(data);
    }

    private static ObjectNode batch(String... rows) {
        return (ObjectNode) JSON.readTree("{\"cid\":\"%s\",\"recordSet\":[%s]}".formatted(CID, String.join(",", rows)));
    }

    private static InboundMessage part(boolean finalMessage) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", NOW), "{}");
        message.markBuffered(CID, finalMessage ? 1 : null, 1);
        return message;
    }

    private static InboundMessage processedPart() {
        InboundMessage message = part(false);
        message.markProcessed(NOW);
        return message;
    }
}
