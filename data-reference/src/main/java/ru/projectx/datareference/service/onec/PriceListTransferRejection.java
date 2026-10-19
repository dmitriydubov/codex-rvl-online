package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.stream.PriceListMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.inbound.RejectionScope;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PriceListTransferRejection implements RejectionScope {

    private final InboundMessageRepository repository;
    private final PriceTransferServiceLock serviceLock;
    private final PriceTransferServiceCheck serviceCheck;
    private final PriceListPartCheck partCheck;
    private final StoredEnvelope envelope;

    public PriceListTransferRejection(InboundMessageRepository repository, PriceTransferServiceLock serviceLock,
            PriceListMapper priceList, ObjectMapper json) {

        this.repository = repository;
        this.serviceLock = serviceLock;
        this.serviceCheck = new PriceTransferServiceCheck(repository);
        this.partCheck = new PriceListPartCheck(priceList);
        this.envelope = new StoredEnvelope(json);
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PRICE_LISTS;
    }

    @Override
    public List<InboundMessage> reject(InboundMessage message, String reason) {
        return partOf(message)
                .map(part -> rejectTransfer(message, part, reason))
                .orElseGet(() -> {
                    message.markFailed(reason);
                    return List.of(message);
                });
    }

    private List<InboundMessage> rejectTransfer(InboundMessage message, TransferPart part, String reason) {
        serviceLock.lockTransferService(part.cid(), part.data());
        List<InboundMessage> parts = repository.findByCorrelationId(part.cid());
        Reasons reasons = reasonsFor(message, part, parts, reason);
        part.markupOrTransferOnly().applyTo(message);
        message.markFailed(reasons.own());
        List<InboundMessage> others = bufferedOthers(message, parts);
        others.forEach(other -> other.markFailed(reasons.others()));
        return Stream.concat(Stream.of(message), others.stream()).toList();
    }

    private Reasons reasonsFor(InboundMessage message, TransferPart part, List<InboundMessage> parts, String reason) {
        UUID cid = part.cid();
        if (hasOwnDefect(message, part, parts)) {
            return new Reasons(reason, "передача %s отклонена: ошибка в сообщении %s: %s"
                    .formatted(cid, message.receipt().messageId(), reason));
        }
        String transferReason = "передача %s отклонена: %s".formatted(cid, reason);
        return new Reasons(transferReason, transferReason);
    }

    private static List<InboundMessage> bufferedOthers(InboundMessage message, List<InboundMessage> parts) {
        UUID messageId = message.receipt().messageId();
        return parts.stream()
                .filter(PriceListTransfer::isCollected)
                .filter(other -> !other.receipt().messageId().equals(messageId))
                .toList();
    }

    private boolean hasOwnDefect(InboundMessage message, TransferPart part, List<InboundMessage> parts) {
        return part.markup()
                .map(markup -> failsOwnChecks(message, part, markup, parts))
                .orElse(true);
    }

    private boolean failsOwnChecks(
            InboundMessage message, TransferPart part, PriceListTransferMarkup markup, List<InboundMessage> parts) {
        try {
            serviceCheck.serviceOf(part.cid(), part.data());
            partCheck.check(message.receipt().messageId(), markup, part.data(), parts);
            return false;
        }
        catch (OneCFieldException defect) {
            return true;
        }
    }

    private Optional<TransferPart> partOf(InboundMessage message) {
        try {
            return envelope.dataOf(message)
                    .map(data -> new TransferPart(PriceListTransferMarkup.cidOf(data), data));
        }
        catch (OneCFieldException notTransferPart) {
            return Optional.empty();
        }
    }

    private record TransferPart(UUID cid, ObjectNode data) {

        Optional<PriceListTransferMarkup> markup() {
            try {
                return Optional.of(PriceListTransferMarkup.of(data));
            }
            catch (OneCFieldException unreadableCounter) {
                return Optional.empty();
            }
        }

        PriceListTransferMarkup markupOrTransferOnly() {
            return markup().orElseGet(() -> new PriceListTransferMarkup(cid, null, 0));
        }
    }

    private record Reasons(String own, String others) {
    }
}
