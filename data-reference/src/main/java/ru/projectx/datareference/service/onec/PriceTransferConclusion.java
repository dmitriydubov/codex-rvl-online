package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.service.InboundMessageService;

@Component
public class PriceTransferConclusion {

    private static final Logger log = LoggerFactory.getLogger(PriceTransferConclusion.class);

    private final InboundMessageService inboundMessageService;
    private final PriceListReplacement replacement;
    private final Clock clock;

    public PriceTransferConclusion(
            InboundMessageService inboundMessageService, PriceListReplacement replacement, Clock clock) {
        this.inboundMessageService = inboundMessageService;
        this.replacement = replacement;
        this.clock = clock;
    }

    ImportOutcome conclude(PriceListTransfer transfer, ParsedTransferParts parsed) {
        return switch (transfer) {
            case PriceListTransfer.Pending pending -> ImportOutcome.skipped(pending.reason());
            case PriceListTransfer.Rejected rejected -> rejectLate(rejected);
            case PriceListTransfer.Closed closed -> skipLate(closed);
            case PriceListTransfer.Mismatched mismatched -> fail(mismatched);
            case PriceListTransfer.Assembled assembled -> replacement.replace(assembled, parsed, clock.instant());
        };
    }

    private ImportOutcome fail(PriceListTransfer.Mismatched transfer) {
        log.error(transfer.reason());
        saveAll(transfer.fail());
        return ImportOutcome.rejected(transfer.reason());
    }

    private ImportOutcome rejectLate(PriceListTransfer.Rejected transfer) {
        log.warn(transfer.reason());
        saveAll(transfer.fail());
        return ImportOutcome.rejected(transfer.reason());
    }

    private ImportOutcome skipLate(PriceListTransfer.Closed transfer) {
        saveAll(transfer.skip(clock.instant()));
        return ImportOutcome.skipped(transfer.reason());
    }

    private void saveAll(List<InboundMessage> messages) {
        for (InboundMessage message : messages) {
            inboundMessageService.saveTransferPart(message);
        }
    }
}
