package ru.projectx.datareference.service.onec;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PriceListTransferAssembler implements StreamHandler {

    private final InboundMessageService inboundMessageService;
    private final PriceTransferServiceLock serviceLock;
    private final PriceListPartCheck partCheck;
    private final PriceTransferPartsInAdvance partsInAdvance;
    private final PriceTransferConclusion conclusion;

    public PriceListTransferAssembler(
            InboundMessageService inboundMessageService,
            PriceListMapper priceListMapper,
            PriceTransferServiceLock serviceLock,
            PriceTransferPartsInAdvance partsInAdvance,
            PriceTransferConclusion conclusion) {

        this.inboundMessageService = inboundMessageService;
        this.serviceLock = serviceLock;
        this.partCheck = new PriceListPartCheck(priceListMapper);
        this.partsInAdvance = partsInAdvance;
        this.conclusion = conclusion;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PRICE_LISTS;
    }

    @Override
    public ImportOutcome handle(InboundMessage message, ObjectNode data) {
        PriceListTransferMarkup markup = PriceListTransferMarkup.of(data);
        ParsedTransferParts parsed = partsInAdvance.parse(message, markup, data);
        serviceLock.lockServiceOf(markup.cid(), data);
        markup.applyTo(message);
        inboundMessageService.saveTransferPart(message);
        List<InboundMessage> parts = inboundMessageService.transferParts(markup.cid());
        partCheck.check(message.receipt().messageId(), markup, data, parts);
        return conclusion.conclude(PriceListTransfer.of(markup.cid(), parts), parsed);
    }
}
