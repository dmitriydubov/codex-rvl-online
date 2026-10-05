package ru.projectx.datareference.service.onec;

import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Собранная передача прайса в том виде, в каком её принимает замещение прайса: GUID услуги из финального сообщения
 * и строки всех пакетов одним набором по возрастанию номера строки. Порядок задаёт источник, и после доставки
 * двумя очередями восстановить его больше неоткуда.
 */
final class PriceListTransferRows {

    private static final String DATA = "data";
    private static final String KEY_VALUE = "keyValue";
    private static final String RECORD_SET = "recordSet";

    private final ObjectMapper json;

    PriceListTransferRows(ObjectMapper json) {
        this.json = json;
    }

    /** @throws OneCFieldException если тело части стёрто или у строки нет целого номера */
    ObjectNode of(PriceListTransfer.Assembled transfer) {
        ObjectNode assembled = json.createObjectNode();
        assembled.set(KEY_VALUE, dataOf(transfer.finalMessage()).path(KEY_VALUE));

        ArrayNode recordSet = assembled.putArray(RECORD_SET);
        transfer.batches().stream()
                .flatMap(batch -> rowsOf(dataOf(batch)))
                .sorted(Comparator.comparingInt(PriceListRowNumber::of))
                .forEach(recordSet::add);
        return assembled;
    }

    private ObjectNode dataOf(InboundMessage message) {
        String payload = message.payload();
        if (payload == null) {
            throw new OneCFieldException("тело сообщения передачи стёрто, собрать её нечем");
        }
        return (ObjectNode) json.readTree(payload).path(DATA);
    }

    private static Stream<JsonNode> rowsOf(ObjectNode data) {
        return StreamSupport.stream(data.path(RECORD_SET).spliterator(), false);
    }
}
