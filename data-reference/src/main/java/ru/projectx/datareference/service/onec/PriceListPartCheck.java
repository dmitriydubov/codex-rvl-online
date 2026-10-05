package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.stream.StreamSupport;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Ошибки одной части передачи прайса, которые видны, как только часть пришла: строка пакета без номера, цены или
 * даты начала и пакет, пришедший к уже применённой передаче. Такую часть отклоняют её собственной причиной, а
 * остальные части передачи — ссылкой на неё.
 */
final class PriceListPartCheck {

    private static final String RECORD_SET = "recordSet";

    private final PriceListMapper priceList;

    PriceListPartCheck(PriceListMapper priceList) {
        this.priceList = priceList;
    }

    /**
     * Финальное сообщение строк не несёт; часть уже отклонённой передачи отклоняется отказом передачи, а не своим.
     *
     * @param parts сообщения передачи, которые уже есть в сырье
     * @throws OneCFieldException с причиной для этой части
     */
    void check(PriceListTransferMarkup markup, ObjectNode data, List<InboundMessage> parts) {
        if (markup.batchCount() != null || anyIn(parts, InboundStatus.FAILED)) {
            return;
        }
        StreamSupport.stream(data.path(RECORD_SET).spliterator(), false).forEach(this::checkRow);
        if (anyIn(parts, InboundStatus.PROCESSED)) {
            throw new OneCFieldException("лишний пакет: передача %s уже применена".formatted(markup.cid()));
        }
    }

    private static boolean anyIn(List<InboundMessage> parts, InboundStatus status) {
        return parts.stream().anyMatch(part -> part.outcome().status() == status);
    }

    private void checkRow(JsonNode row) {
        PriceListRowNumber.of(row);
        priceList.checkRow(row);
    }
}
