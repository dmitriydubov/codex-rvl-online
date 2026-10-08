package ru.projectx.datareference.service.onec;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.OneCValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Собранная передача прайса для замещения: GUID услуги из финального сообщения и строки всех пакетов одним набором.
 * Строки сортируются по номеру, потому что после доставки двумя очередями другого источника порядка нет.
 */
final class PriceListTransferRows {

    private static final String KEY_VALUE = "keyValue";
    private static final String RECORD_SET = "recordSet";

    private final ObjectMapper json;
    private final StoredEnvelope envelope;

    PriceListTransferRows(ObjectMapper json) {
        this.json = json;
        this.envelope = new StoredEnvelope(json);
    }

    /**
     * @param parsed тела частей, разобранные заранее; сырьё читается только для части, которой среди них нет
     * @throws OneCFieldException если тело части стёрто или не читается, или у строки нет целого номера
     */
    ObjectNode of(PriceListTransfer.Assembled transfer, ParsedTransferParts parsed) {
        ObjectNode assembled = json.createObjectNode();
        assembled.set(KEY_VALUE, dataOf(transfer.finalMessage(), parsed).path(KEY_VALUE));

        ArrayNode recordSet = assembled.putArray(RECORD_SET);
        transfer.batches().stream()
                .flatMap(batch -> rowsOf(dataOf(batch, parsed)))
                .sorted(Comparator.comparingInt(PriceListRowNumber::of))
                .forEach(recordSet::add);
        return assembled;
    }

    /** @throws OneCFieldException если в финальном сообщении нет GUID услуги */
    static UUID serviceOf(ObjectNode assembled) {
        return OneCValues.requiredUuid(assembled, KEY_VALUE);
    }

    private ObjectNode dataOf(InboundMessage message, ParsedTransferParts parsed) {
        return parsed.dataOf(message).or(() -> envelope.dataOf(message)).orElseThrow(
                () -> new OneCFieldException("тело сообщения передачи стёрто, собрать её нечем"));
    }

    private static Stream<JsonNode> rowsOf(ObjectNode data) {
        return StreamSupport.stream(data.path(RECORD_SET).spliterator(), false);
    }
}
