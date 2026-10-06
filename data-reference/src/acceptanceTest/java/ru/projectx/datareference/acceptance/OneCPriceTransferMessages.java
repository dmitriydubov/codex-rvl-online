package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Части передачи прайса по услуге (ТЗ-9) с заданными сценарием {@code message-id} и идентификатором передачи:
 * финальное сообщение со счётчиками и пакеты строк, в том числе с испорченной строкой. Все значения вымышлены.
 */
final class OneCPriceTransferMessages {

    private static final String BATCH_QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes";
    private static final String FINAL_QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes-final";
    private static final String ROW_NUMBER = "rowNumber";
    private static final String TABLE_ROW_NUMBER = "номер строки";
    private static final String CID = "cid";
    private static final String KEY_VALUE = "keyValue";
    /** Так сценарий называет часть, в которой поля GUID услуги нет вовсе. */
    private static final String NO_SERVICE = "нет";

    /** Строка пакета из одной строки, от которой сценарий отличает испорченную: номер 2, цена 6000.00. */
    private static final Map<String, String> SINGLE_ROW = Map.of(
            "начало", "2026-05-01", "окончание", "3000-01-01", "цена", "6000.00", "цена со скидкой", "нет");

    /** Дефекты строки так, как их называет спецификация; незнакомое слово роняет шаг. */
    private static final Map<String, Consumer<ObjectNode>> DEFECTS = Map.of(
            "нет цены", row -> row.remove("price"),
            "цена больше допустимого", row -> row.put("price", new BigDecimal("10000000000000.00")),
            "нет номера строки", row -> row.remove(ROW_NUMBER),
            "номер строки — текст", row -> row.put(ROW_NUMBER, "вторая"),
            "номер строки — дробное число", row -> row.put(ROW_NUMBER, new BigDecimal("2.5")));

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCPriceTransferMessages() {
    }

    static OneCMessage finalMessage(UUID messageId, UUID cid, String service, int batches, int rows) {
        ObjectNode data = transferData(cid, service)
                .put("countMessages", batches)
                .put("countRows", rows);
        return new OneCMessage(OneCStream.PRICE_LISTS, FINAL_QUEUE, data, InboundStatus.PROCESSED, messageId);
    }

    /** Пакет со строками таблицы; номер строки в таблице — число. */
    static OneCMessage batch(UUID messageId, UUID cid, String service, List<Map<String, String>> rows) {
        ArrayNode recordSet = JSON.createArrayNode();
        rows.forEach(row -> recordSet.add(numberedRow(row)));
        return batchMessage(messageId, transferData(cid, service), recordSet, InboundStatus.PROCESSED);
    }

    /** Пакет из одной строки с номером 2, испорченной дефектом. */
    static OneCMessage defectiveBatch(UUID messageId, UUID cid, String service, String defect) {
        if (!DEFECTS.containsKey(defect)) {
            throw new IllegalArgumentException("дефект строки, которого шаг не знает: " + defect);
        }
        ObjectNode row = OneCServiceMessages.priceRow(2, SINGLE_ROW);
        DEFECTS.get(defect).accept(row);
        return batchMessage(messageId, transferData(cid, service), JSON.createArrayNode().add(row), InboundStatus.FAILED);
    }

    /** Пакет со строками таблицы, но без идентификатора передачи. */
    static OneCMessage batchWithoutTransfer(UUID messageId, String service, List<Map<String, String>> rows) {
        ArrayNode recordSet = JSON.createArrayNode();
        rows.forEach(row -> recordSet.add(numberedRow(row)));
        return batchMessage(messageId, JSON.createObjectNode().put(KEY_VALUE, service), recordSet, InboundStatus.FAILED);
    }

    /** Услуга кладётся в keyValue как есть, в том числе не GUID; «нет» — поля keyValue в части нет. */
    private static ObjectNode transferData(UUID cid, String service) {
        ObjectNode data = JSON.createObjectNode().put(CID, cid.toString());
        return NO_SERVICE.equals(service) ? data : data.put(KEY_VALUE, service);
    }

    private static OneCMessage batchMessage(UUID messageId, ObjectNode data, ArrayNode recordSet, InboundStatus expected) {
        data.set("recordSet", recordSet);
        return new OneCMessage(OneCStream.PRICE_LISTS, BATCH_QUEUE, data, expected, messageId);
    }

    private static ObjectNode numberedRow(Map<String, String> row) {
        Map<String, String> fields = new HashMap<>(row);
        int number = Integer.parseInt(StepTables.required(row, TABLE_ROW_NUMBER));
        fields.remove(TABLE_ROW_NUMBER);
        return OneCServiceMessages.priceRow(number, fields);
    }
}
