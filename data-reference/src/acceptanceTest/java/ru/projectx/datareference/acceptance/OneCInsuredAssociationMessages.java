package ru.projectx.datareference.acceptance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class OneCInsuredAssociationMessages {

    static final String REPRESENTATION = "Тестов Пётр Иванович";

    private static final String ROWS_QUEUE = "1c-usk_to_rvl-online_insured-association-changes";
    private static final String FINAL_QUEUE = "1c-usk_to_rvl-online_insured-association-changes-final";
    private static final String OTHER_SIDE = "otherSide";
    private static final String VALUE = "value";
    private static final String INACTIVE = " (неактивен)";
    private static final String POLICY_REFERENCE = "CatalogRef.ПолисаЗастрахованных";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCInsuredAssociationMessages() {
    }

    static OneCMessage rows(UUID messageId, UUID cid, String filter, List<Map<String, String>> rows, int firstRow) {
        ArrayNode recordSet = JSON.createArrayNode();
        for (int index = 0; index < rows.size(); index++) {
            recordSet.add(row(firstRow + index, rows.get(index)));
        }
        ObjectNode data = transfer(cid, filter);
        data.set("recordSet", recordSet);
        return new OneCMessage(OneCStream.INSURED_ASSOCIATIONS, ROWS_QUEUE, data, InboundStatus.PROCESSED, messageId);
    }

    static OneCMessage finalMessage(UUID messageId, UUID cid, String filter, int messages, int rows) {
        ObjectNode data = transfer(cid, filter).put("countMessages", messages).put("countRows", rows);
        return new OneCMessage(OneCStream.INSURED_ASSOCIATIONS, FINAL_QUEUE, data, InboundStatus.PROCESSED, messageId);
    }

    private static ObjectNode transfer(UUID cid, String filter) {
        ObjectNode data = JSON.createObjectNode();
        data.set("filter", filter(filter));
        return data.put("mode", "Замещение").put("cid", cid.toString());
    }

    private static ObjectNode row(int rowNumber, Map<String, String> row) {
        ObjectNode data = JSON.createObjectNode()
                .put("periodTo", StepTables.required(row, "окончание"))
                .put("relationType", StepTables.required(row, "тип связи"))
                .put("idInsured", StepTables.required(row, "застрахованный"))
                .put("period", StepTables.required(row, "начало"))
                .put("rowNumber", rowNumber);
        data.set(OTHER_SIDE, policyReference(StepTables.required(row, "связанный полис")));
        return data;
    }

    private static ArrayNode filter(String spec) {
        ArrayNode elements = JSON.createArrayNode();
        for (String element : spec.split(";")) {
            elements.add(element(element.strip()));
        }
        return elements;
    }

    private static ObjectNode element(String spec) {
        boolean inactive = spec.endsWith(INACTIVE);
        String[] parts = spec.replace(INACTIVE, "").split("=", 2);
        ObjectNode element = JSON.createObjectNode();
        if (OTHER_SIDE.equals(parts[0])) {
            element.set(VALUE, policyReference(parts[1]));
        }
        else {
            element.put(VALUE, parts[1]);
        }
        return element.put("isActive", !inactive).put("fieldName", parts[0]);
    }

    private static ObjectNode policyReference(String policy) {
        return JSON.createObjectNode().put("type", POLICY_REFERENCE).put("representation", REPRESENTATION)
                .put(VALUE, policy);
    }
}
