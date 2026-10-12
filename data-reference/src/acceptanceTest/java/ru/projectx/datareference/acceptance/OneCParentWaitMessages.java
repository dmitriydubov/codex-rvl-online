package ru.projectx.datareference.acceptance;

import static ru.projectx.datareference.acceptance.StepTables.onlyKnown;
import static ru.projectx.datareference.acceptance.StepTables.required;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OneCParentWaitMessages {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTACTS_QUEUE = "1c-usk_to_rvl-online_contact-information-changes";
    private static final String LPU_CONTRACTS_QUEUE = "1c-usk_to_rvl-online_contragents-contracts-changes";
    private static final String CONTRAGENT = "контрагент";
    private static final String VALUE = "value";
    private static final String OBJECT = "object";

    private OneCParentWaitMessages() {
    }

    static OneCMessage sanatoriumWithoutRvl(Map<String, String> table) {
        onlyKnown(table, "external_id", "наименование");
        ObjectNode data = JSON.createObjectNode()
                .put("guid", required(table, "external_id"))
                .put("thisIsLPU", true)
                .put("deleteMark", false)
                .put("Наименование", required(table, "наименование"));
        data.putArray("typesActivities");
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data, InboundStatus.SKIPPED);
    }

    static OneCMessage contragentContacts(Map<String, String> table) {
        onlyKnown(table, OneCMessages.MESSAGE_ID, CONTRAGENT, "адрес", "регион");
        String contragent = required(table, CONTRAGENT);
        ObjectNode data = JSON.createObjectNode();
        data.putArray("filter").addObject()
                .put("fieldName", OBJECT)
                .putObject(VALUE).put(VALUE, contragent);
        ObjectNode address = data.putArray("recordSet").addObject()
                .put("type", "Адрес")
                .put("rowNumber", 1)
                .put("representation", required(table, "адрес"))
                .put("field2", required(table, "регион"));
        address.putObject("kind").put("representation", "Фактический адрес контрагента");
        address.putObject(OBJECT).put(VALUE, contragent);
        return new OneCMessage(OneCStream.LPU_CONTACTS, CONTACTS_QUEUE, data, InboundStatus.PROCESSED,
                OneCMessages.fixedMessageId(table));
    }

    static List<OneCMessage> contractsOfUnknownSanatoriums(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(OneCParentWaitMessages::contractOfUnknownSanatorium).toList();
    }

    private static OneCMessage contractOfUnknownSanatorium(int number) {
        ObjectNode data = JSON.createObjectNode()
                .put("id", guid("договор", number))
                .put("number", "Д-ПАЧКА-" + number)
                .put("ownerId", guid("санаторий", number))
                .put("usePricesMainAgreement", false)
                .put("beginDate", "2020-01-01")
                .put("endDate", "3000-01-01")
                .put("deleteMark", false);
        return new OneCMessage(OneCStream.LPU_CONTRACTS, LPU_CONTRACTS_QUEUE, data, InboundStatus.AWAITING_PARENT);
    }

    private static String guid(String kind, int number) {
        return UUID.nameUUIDFromBytes((kind + number).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
