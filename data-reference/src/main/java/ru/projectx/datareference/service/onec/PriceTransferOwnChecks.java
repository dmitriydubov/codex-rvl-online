package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import tools.jackson.databind.node.ObjectNode;

record PriceTransferOwnChecks(
        PriceTransferServiceLock serviceLock, PriceTransferServiceCheck serviceCheck, TransferPartCheck partCheck)
        implements TransferOwnChecks {

    @Override
    public void lockTransfer(UUID cid, ObjectNode part) {
        serviceLock.lockTransferService(cid, part);
    }

    @Override
    public void check(InboundMessage message, TransferMarkup markup, ObjectNode part, List<InboundMessage> parts) {
        serviceCheck.serviceOf(markup.cid(), part);
        partCheck.check(message.receipt().messageId(), markup, part, parts);
    }
}
