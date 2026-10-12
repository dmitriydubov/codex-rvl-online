package ru.projectx.datareference.service.onec;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PriceTransferServiceLock {

    private final PriceTransferServiceCheck check;
    private final InboundMessageRepository inbound;
    private final PriceListFinalizationRepository finalizations;

    public PriceTransferServiceLock(InboundMessageRepository inbound, PriceListFinalizationRepository finalizations) {
        this.check = new PriceTransferServiceCheck(inbound);
        this.inbound = inbound;
        this.finalizations = finalizations;
    }

    void lockServiceOf(UUID cid, ObjectNode part) {
        check.serviceOf(cid, part).ifPresent(finalizations::lockByService);
    }

    void lockTransferService(UUID cid, ObjectNode part) {
        inbound.findBufferedTransferService(cid).flatMap(TransferServiceGuid::parse)
                .or(() -> TransferServiceGuid.readableIn(part))
                .ifPresent(finalizations::lockByService);
    }
}
