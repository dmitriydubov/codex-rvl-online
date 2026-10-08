package ru.projectx.datareference.service.onec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор тел частей собирающейся передачи прайса заранее, вне транзакции: долгий разбор под блокировкой задерживал бы
 * другие решения по той же услуге. Сборка под блокировкой всё равно перечитывает части и сама разбирает недостающие.
 * Разобранное здесь тело она берёт как есть, потому что тела частей в буфере и ждущих услугу по сроку не стираются.
 */
@Component
public class PriceTransferPartsInAdvance {

    private final PriceTransferPartsReader reader;
    private final StoredEnvelope envelope;

    public PriceTransferPartsInAdvance(PriceTransferPartsReader reader, ObjectMapper json) {
        this.reader = reader;
        this.envelope = new StoredEnvelope(json);
    }

    /**
     * Разбирает части, только если вместе с пришедшей частью передача выглядит собранной. Вызывается до первого
     * запроса транзакции разбора.
     */
    ParsedTransferParts parse(InboundMessage received, PriceListTransferMarkup markup, ObjectNode data) {
        List<InboundMessage> parts = withReceivedBuffered(reader.parts(markup.cid()), received, markup);
        if (PriceListTransfer.of(markup.cid(), parts) instanceof PriceListTransfer.Assembled assembled) {
            return parsed(assembled, received.receipt().messageId(), data);
        }
        return ParsedTransferParts.NONE;
    }

    /**
     * Части передачи такими, какими они станут, когда пришедшая часть ляжет в буфер. Вместо пришедшей части берётся её
     * копия с разметкой: ни прочитанные части, ни сама пришедшая часть при этом не меняются.
     */
    private static List<InboundMessage> withReceivedBuffered(
            List<InboundMessage> stored, InboundMessage received, PriceListTransferMarkup markup) {

        UUID receivedId = received.receipt().messageId();
        InboundMessage buffered = InboundMessage.receive(received.receipt(), received.payload());
        markup.applyTo(buffered);
        return Stream.concat(
                        stored.stream().filter(part -> !part.receipt().messageId().equals(receivedId)),
                        Stream.of(buffered))
                .toList();
    }

    /** Обычный цикл, а не стрим: у каждой части свой источник данных, и не у каждой они есть. */
    private ParsedTransferParts parsed(PriceListTransfer.Assembled transfer, UUID receivedId, ObjectNode receivedData) {
        Map<UUID, ObjectNode> dataByMessage = new HashMap<>();
        for (InboundMessage part : partsOf(transfer)) {
            UUID messageId = part.receipt().messageId();
            Optional<ObjectNode> data = messageId.equals(receivedId) ? Optional.of(receivedData) : readableData(part);
            data.ifPresent(parsedData -> dataByMessage.put(messageId, parsedData));
        }
        return new ParsedTransferParts(dataByMessage);
    }

    private static List<InboundMessage> partsOf(PriceListTransfer.Assembled transfer) {
        return Stream.concat(Stream.of(transfer.finalMessage()), transfer.batches().stream()).toList();
    }

    /**
     * Стёртое или нечитаемое тело заранее не разбирается: отказ такой части сборка назовёт сама, после проверок,
     * которые идут раньше разбора частей.
     */
    private Optional<ObjectNode> readableData(InboundMessage part) {
        try {
            return envelope.dataOf(part);
        }
        catch (OneCFieldException unreadable) {
            return Optional.empty();
        }
    }
}
