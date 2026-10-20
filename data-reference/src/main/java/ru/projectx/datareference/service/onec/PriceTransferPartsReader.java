package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;

@Component
public class PriceTransferPartsReader {

    private final InboundMessageRepository inbound;

    public PriceTransferPartsReader(InboundMessageRepository inbound) {
        this.inbound = inbound;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<InboundMessage> parts(UUID cid) {
        return inbound.findByCorrelationId(cid);
    }
}
