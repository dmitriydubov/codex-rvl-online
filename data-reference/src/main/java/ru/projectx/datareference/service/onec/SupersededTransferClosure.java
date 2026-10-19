package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.inbound.InboundMessageService;

@Component
public class SupersededTransferClosure {

    private static final Logger log = LoggerFactory.getLogger(SupersededTransferClosure.class);

    private final InboundMessageRepository repository;
    private final PriceTransferOrder order;
    private final InboundMessageService inboundMessageService;
    private final Clock clock;

    public SupersededTransferClosure(InboundMessageRepository repository, PriceTransferOrder order,
            InboundMessageService inboundMessageService, Clock clock) {

        this.repository = repository;
        this.order = order;
        this.inboundMessageService = inboundMessageService;
        this.clock = clock;
    }

    @Transactional
    public boolean closeIfSuperseded(UUID cid) {
        return repository.findBufferedTransferService(cid)
                .flatMap(TransferServiceGuid::parse)
                .flatMap(order::lockApplied)
                .map(applied -> closeIfOlder(cid, applied))
                .orElse(false);
    }

    private boolean closeIfOlder(UUID cid, AppliedPriceTransfer applied) {
        List<InboundMessage> buffered = inboundMessageService.transferParts(cid).stream()
                .filter(part -> part.outcome().status() == InboundStatus.BUFFERED)
                .toList();
        if (!applied.supersedes(buffered)) {
            return false;
        }
        skipAll(buffered, applied.reason());
        log.info("передача прайса {} закрыта пропуском: {}", cid, applied.reason());
        return true;
    }

    private void skipAll(List<InboundMessage> parts, String reason) {
        Instant now = clock.instant();
        for (InboundMessage part : parts) {
            part.markSkipped(reason, now);
            inboundMessageService.saveTransferPart(part);
        }
    }
}
