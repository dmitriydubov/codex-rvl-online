package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.RejectionScope;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Окончательный отказ части передачи прайса отклоняет и все части этой передачи, ещё лежащие в буфере: иначе
 * передача навсегда осталась бы наполовину собранной.
 *
 * <p>Ошибка в самой части ({@link PriceListPartCheck}) остаётся её причиной, а остальные части ссылаются на неё.
 * Отказ при сборке (база отклонила запись, исчерпаны повторы) ни одной части не принадлежит, и у всех частей одна
 * причина передачи. Часть без читаемого идентификатора передачи отклоняется одна.
 */
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

    /**
     * Строка услуги передачи блокируется до чтения частей, как при разборе: закрытие зависших не пишет их
     * одновременно, даже если отклоняемая часть называет другую услугу.
     */
    private List<InboundMessage> rejectTransfer(InboundMessage message, TransferPart part, String reason) {
        serviceLock.lockTransferService(part.cid(), part.data());
        List<InboundMessage> parts = repository.findByCorrelationId(part.cid());
        Reasons reasons = reasonsFor(message, part, parts, reason);
        part.markup().ifPresent(markup -> markup.applyTo(message));
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

    /**
     * Те же проверки, что прервали разбор части: если они находят ошибку, отказ принадлежит этой части. Нечитаемая
     * разметка — тоже ошибка самой части.
     */
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

    /**
     * Часть передачи, если в её теле читается идентификатор передачи. Нечитаемое тело или идентификатор — тоже
     * окончательный отказ, но отнести его к передаче нельзя, и такая часть отклоняется одна.
     */
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

        /**
         * Разметка части, если она читается. Нечитаемый счётчик — ошибка самой части: передача находится по
         * идентификатору, а разметка в часть не записывается.
         */
        Optional<PriceListTransferMarkup> markup() {
            try {
                return Optional.of(PriceListTransferMarkup.of(data));
            }
            catch (OneCFieldException unreadableCounter) {
                return Optional.empty();
            }
        }
    }

    /** Причина у отклонённой части и у остальных частей её передачи. */
    private record Reasons(String own, String others) {
    }
}
