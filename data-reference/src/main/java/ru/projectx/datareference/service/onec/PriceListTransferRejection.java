package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.PriceListMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.RejectionScope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
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

    private static final String DATA = "data";

    private final InboundMessageRepository repository;
    private final PriceTransferOrder order;
    private final PriceListPartCheck partCheck;
    private final ObjectMapper json;

    public PriceListTransferRejection(InboundMessageRepository repository, PriceTransferOrder order,
            PriceListMapper priceList, ObjectMapper json) {

        this.repository = repository;
        this.order = order;
        this.partCheck = new PriceListPartCheck(priceList);
        this.json = json;
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

    /** Строка услуги блокируется до чтения частей, как при разборе: закрытие зависших не пишет их одновременно. */
    private List<InboundMessage> rejectTransfer(InboundMessage message, TransferPart part, String reason) {
        order.lockServiceOf(part.data());
        List<InboundMessage> parts = repository.findByCorrelationId(part.markup().cid());
        Reasons reasons = reasonsFor(message, part, parts, reason);
        part.markup().applyTo(message);
        message.markFailed(reasons.own());
        List<InboundMessage> others = bufferedOthers(message, parts);
        others.forEach(other -> other.markFailed(reasons.others()));
        return Stream.concat(Stream.of(message), others.stream()).toList();
    }

    private Reasons reasonsFor(InboundMessage message, TransferPart part, List<InboundMessage> parts, String reason) {
        UUID cid = part.markup().cid();
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
                .filter(other -> other.outcome().status() == InboundStatus.BUFFERED)
                .filter(other -> !other.receipt().messageId().equals(messageId))
                .toList();
    }

    /** Та же проверка, что прервала разбор части: если она находит ошибку, отказ принадлежит этой части. */
    private boolean hasOwnDefect(InboundMessage message, TransferPart part, List<InboundMessage> parts) {
        try {
            partCheck.check(message.receipt().messageId(), part.markup(), part.data(), parts);
            return false;
        }
        catch (OneCFieldException defect) {
            return true;
        }
    }

    /**
     * Разметка части, если её можно прочитать. Нечитаемое тело или идентификатор — тоже окончательный отказ, но
     * отнести его к передаче нельзя, и такая часть отклоняется одна.
     */
    private Optional<TransferPart> partOf(InboundMessage message) {
        try {
            return Optional.ofNullable(message.payload())
                    .map(payload -> json.readTree(payload).path(DATA))
                    .filter(JsonNode::isObject)
                    .map(data -> new TransferPart(PriceListTransferMarkup.of((ObjectNode) data), (ObjectNode) data));
        }
        catch (JacksonException | OneCFieldException notTransferPart) {
            return Optional.empty();
        }
    }

    private record TransferPart(PriceListTransferMarkup markup, ObjectNode data) {
    }

    /** Причина у отклонённой части и у остальных частей её передачи. */
    private record Reasons(String own, String others) {
    }
}
