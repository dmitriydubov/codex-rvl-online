package ru.projectx.datareference.service.onec;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Замещение прайса услуги собранной передачей с учётом порядка передач: строки всех пакетов идут в замещение одним
 * набором, если по услуге не применена более новая передача. Исход замещения записывается сразу всем частям
 * передачи: применены, пропущены или ждут услугу, которой ещё нет в справочнике.
 */
@Component
public class PriceListReplacement {

    private final PriceListMapper priceListMapper;
    private final PriceTransferOrder order;
    private final PriceListTransferRows rows;
    private final InboundMessageService inboundMessageService;

    public PriceListReplacement(PriceListMapper priceListMapper, PriceTransferOrder order, ObjectMapper json,
            InboundMessageService inboundMessageService) {

        this.priceListMapper = priceListMapper;
        this.order = order;
        this.rows = new PriceListTransferRows(json);
        this.inboundMessageService = inboundMessageService;
    }

    /**
     * Работает в транзакции вызывающего под блокировкой строки услуги, поэтому исход фиксируется вместе с ценами.
     *
     * @throws OneCFieldException если тело части стёрто, у строки нет номера или в передаче нет GUID услуги
     */
    ImportOutcome replace(PriceListTransfer.Assembled transfer, ParsedTransferParts parsed, Instant at) {
        ObjectNode assembled = rows.of(transfer, parsed);
        ImportResult result = order.applyInOrder(PriceListTransferRows.serviceOf(assembled), transfer.finalMessage(),
                () -> priceListMapper.apply(assembled));
        for (InboundMessage part : transfer.markOutcome(result, at)) {
            inboundMessageService.saveTransferPart(part);
        }
        return ImportOutcome.of(result);
    }
}
