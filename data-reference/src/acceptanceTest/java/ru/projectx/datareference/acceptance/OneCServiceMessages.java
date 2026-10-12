package ru.projectx.datareference.acceptance;

import static ru.projectx.datareference.acceptance.StepTables.flagOr;
import static ru.projectx.datareference.acceptance.StepTables.onlyKnown;
import static ru.projectx.datareference.acceptance.StepTables.required;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class OneCServiceMessages {

    private static final String SERVICES_QUEUE = "1c-usk_to_rvl-online_contragents-services-changes";
    private static final String PRICE_BATCH_QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes";
    private static final String PRICE_FINAL_QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes-final";

    private static final String APPEAL_TYPE = "вид обращения";
    private static final String DELETE_MARK = "пометка удаления";
    private static final String NO_DISCOUNT = "нет";
    private static final String CID = "cid";
    private static final String KEY_VALUE = "keyValue";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCServiceMessages() {
    }

    static OneCMessage service(Map<String, String> table) {
        onlyKnown(table, "external_id", "договор", "наименование", "код", APPEAL_TYPE, DELETE_MARK,
                OneCMessages.MESSAGE_ID);
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, "external_id"))
                .put("ownerId", required(table, "договор"))
                .put("name", required(table, "наименование"))
                .put("code", required(table, "код"))
                .put("deleteMark", flagOr(table, DELETE_MARK, false));
        Optional.ofNullable(table.get(APPEAL_TYPE)).ifPresent(appealType -> data.put("typeAppealId", appealType));
        return new OneCMessage(OneCStream.CONTRAGENT_SERVICES, SERVICES_QUEUE, data, InboundStatus.PROCESSED,
                OneCMessages.fixedMessageId(table));
    }

    static List<OneCMessage> priceList(String serviceExternalId, List<Map<String, String>> rows) {
        ArrayNode recordSet = JSON.createArrayNode();
        for (Map<String, String> row : rows) {
            recordSet.add(priceRow(recordSet.size() + 1, row));
        }
        UUID cid = UUID.nameUUIDFromBytes((serviceExternalId + recordSet).getBytes(StandardCharsets.UTF_8));
        ObjectNode batch = JSON.createObjectNode()
                .put(CID, cid.toString())
                .put(KEY_VALUE, serviceExternalId);
        batch.set("recordSet", recordSet);
        ObjectNode last = JSON.createObjectNode()
                .put(CID, cid.toString())
                .put(KEY_VALUE, serviceExternalId)
                .put("countMessages", 1)
                .put("countRows", rows.size());
        return List.of(
                new OneCMessage(OneCStream.PRICE_LISTS, PRICE_BATCH_QUEUE, batch, InboundStatus.PROCESSED),
                new OneCMessage(OneCStream.PRICE_LISTS, PRICE_FINAL_QUEUE, last, InboundStatus.PROCESSED));
    }

    static ObjectNode priceRow(int rowNumber, Map<String, String> row) {
        onlyKnown(row, "начало", "окончание", "цена", "цена со скидкой");
        ObjectNode line = JSON.createObjectNode()
                .put("rowNumber", rowNumber)
                .put("period", required(row, "начало"))
                .put("periodTo", required(row, "окончание"))
                .put("price", new BigDecimal(required(row, "цена")));
        String discounted = required(row, "цена со скидкой");
        if (!NO_DISCOUNT.equals(discounted)) {
            line.put("priceWithDiscount", new BigDecimal(discounted));
        }
        return line;
    }
}
