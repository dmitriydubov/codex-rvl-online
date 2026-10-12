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
