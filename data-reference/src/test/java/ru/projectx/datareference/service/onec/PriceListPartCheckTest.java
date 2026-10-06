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
 * Защищает то, что отклоняет одну часть передачи прайса, ещё до сборки: строку без номера, цены или даты, пакет,
 * пришедший к уже применённой передаче, и второе финальное сообщение. Такая часть отклоняется своей причиной, остальные — ссылкой на неё.
 */
class PriceListPartCheckTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID SELF = UUID.fromString("8e4b0f70-0000-4000-8000-0000000000aa");

    private final PriceListMapper priceList = mock(PriceListMapper.class);
    private final PriceListPartCheck check = new PriceListPartCheck(priceList);

    @Test
    void batchWithNumberedRows_passes_andEveryRowIsCheckedByPriceListRules() {
        ObjectNode data = batch("{\"rowNumber\":1}", "{\"rowNumber\":2}");

        check.check(SELF, markupOf(data), data, List.of());

        InOrder rows = Mockito.inOrder(priceList);
        rows.verify(priceList).checkRow(data.path("recordSet").get(0));
        rows.verify(priceList).checkRow(data.path("recordSet").get(1));
    }

    @Test
    void rowWithoutNumber_rejectsThePart_beforePriceListRules() {
        ObjectNode data = batch("{\"price\":1}");

        assertThatThrownBy(() -> check.check(SELF, markupOf(data), data, List.of()))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля rowNumber");
        verify(priceList, never()).checkRow(data.path("recordSet").get(0));
    }

    @Test
    void rowBreakingPriceListRules_rejectsThePart() {
        ObjectNode data = batch("{\"rowNumber\":1}");
        Mockito.doThrow(new OneCFieldException("в строке прайса нет цены"))
                .when(priceList).checkRow(data.path("recordSet").get(0));

        assertThatThrownBy(() -> check.check(SELF, markupOf(data), data, List.of()))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("в строке прайса нет цены");
    }

    /** Применённая передача уже получила все обещанные пакеты: ещё один — лишний. */
    @Test
    void batchOfAppliedTransfer_isSurplus() {
        ObjectNode data = batch("{\"rowNumber\":1}");

        assertThatThrownBy(() -> check.check(SELF, markupOf(data), data, List.of(part(true), processedPart())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("лишний пакет: передача " + CID + " уже применена");
    }

    @Test
    void batchOfTransferStillInBuffer_isNotSurplus() {
        ObjectNode data = batch("{\"rowNumber\":1}");

        assertThatCode(() -> check.check(SELF, markupOf(data), data, List.of(part(true), part(false))))
                .doesNotThrowAnyException();
    }

    /** Передача уже отклонена: опоздавшая часть разделяет её отказ, даже если испорчена сама. */
    @Test
    void partOfRejectedTransfer_isNotChecked() {
        ObjectNode data = batch("{\"price\":1}");
        InboundMessage rejected = part(false);
        rejected.markFailed("в строке прайса нет цены");

        assertThatCode(() -> check.check(SELF, markupOf(data), data, List.of(rejected, part(true))))
                .doesNotThrowAnyException();
        verifyNoInteractions(priceList);
    }

    /** Отказ лишнего пакета не делает применённую передачу отклонённой: следующий лишний пакет — тоже лишний. */
    @Test
    void secondSurplusBatchOfAppliedTransfer_isSurplus() {
        ObjectNode data = batch("{\"rowNumber\":1}");
        InboundMessage firstSurplus = part(false);
        firstSurplus.markFailed("лишний пакет");

        assertThatThrownBy(() -> check.check(SELF, markupOf(data), data, List.of(processedPart(), firstSurplus)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("лишний пакет: передача " + CID + " уже применена");
    }

    /** У финального сообщения строк нет: правилам прайса проверять нечего. */
    @Test
    void finalMessage_hasNoRowsToCheck() {
        ObjectNode data = finalData();

        assertThatCode(() -> check.check(SELF, markupOf(data), data, List.of(processedPart())))
                .doesNotThrowAnyException();
        verifyNoInteractions(priceList);
    }

    /** Финальное сообщение уже лежит в буфере среди частей своей передачи: само себе оно не второе. */
    @Test
    void finalMessage_amongPartsOfItsTransfer_isNotSecond() {
        ObjectNode data = finalData();

        assertThatCode(() -> check.check(SELF, markupOf(data), data, List.of(part(SELF, true), part(false))))
                .doesNotThrowAnyException();
    }

    /** Финальное сообщение у передачи ровно одно: второе к ещё не применённой передаче — ошибка этой части. */
    @Test
    void secondFinalMessage_ofTransferInBuffer_rejectsThePart() {
        ObjectNode data = finalData();

        assertThatThrownBy(() -> check.check(SELF, markupOf(data), data, List.of(part(true), part(SELF, true))))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("второе финальное сообщение передачи " + CID);
    }

    @Test
    void secondFinalMessage_ofAppliedTransfer_namesTheAppliedTransfer() {
        ObjectNode data = finalData();
        InboundMessage appliedFinal = part(true);
        appliedFinal.markProcessed(NOW);

        assertThatThrownBy(() -> check.check(SELF, markupOf(data), data, List.of(appliedFinal, processedPart())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("второе финальное сообщение: передача " + CID + " уже применена");
    }

    /** Передача уже пропущена: опоздавшая часть разделяет пропуск, даже если испорчена сама или это второй финал. */
    @Test
    void partOfSkippedTransfer_isNotChecked() {
        ObjectNode batch = batch("{\"price\":1}");
        ObjectNode secondFinal = finalData();
        InboundMessage skippedFinal = part(true);
        skippedFinal.markSkipped("прайс по услуге вне справочника", NOW);

        assertThatCode(() -> check.check(SELF, markupOf(batch), batch, List.of(skippedFinal))).doesNotThrowAnyException();
        assertThatCode(() -> check.check(SELF, markupOf(secondFinal), secondFinal, List.of(skippedFinal)))
                .doesNotThrowAnyException();
        verifyNoInteractions(priceList);
    }

    /** Второй финал к отклонённой передаче разделяет её отказ, а не получает свой. */
    @Test
    void secondFinalMessage_ofRejectedTransfer_isNotChecked() {
        ObjectNode data = finalData();
        InboundMessage rejectedFinal = part(true);
        rejectedFinal.markFailed("счётчики не сошлись");

        assertThatCode(() -> check.check(SELF, markupOf(data), data, List.of(rejectedFinal)))
                .doesNotThrowAnyException();
    }

    private static PriceListTransferMarkup markupOf(ObjectNode data) {
        return PriceListTransferMarkup.of(data);
    }

    private static ObjectNode batch(String... rows) {
        return (ObjectNode) JSON.readTree("{\"cid\":\"%s\",\"recordSet\":[%s]}".formatted(CID, String.join(",", rows)));
    }

    private static ObjectNode finalData() {
        return JSON.createObjectNode().put("cid", CID.toString()).put("countMessages", 1).put("countRows", 1);
    }

    private static InboundMessage part(boolean finalMessage) {
        return part(UUID.randomUUID(), finalMessage);
    }

    private static InboundMessage part(UUID messageId, boolean finalMessage) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(messageId, OneCStream.PRICE_LISTS, "очередь", NOW), "{}");
        message.markBuffered(CID, finalMessage ? 1 : null, 1);
        return message;
    }

    private static InboundMessage processedPart() {
        InboundMessage message = part(false);
        message.markProcessed(NOW);
        return message;
    }
}
