package ru.projectx.datareference.acceptance;

import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OneCFailureMessages {

    private static final String CONTRAGENT_ID = "4e3f2a1b-6c5d-4e7f-8a9b-0c1d2e3f4a5b";
    private static final String CONTRAGENT_NAME = "Стенд: контрагент с реквизитами";
    private static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    private static final String DIGITS = "12345678901234567890";

    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTACTS_QUEUE = "1c-usk_to_rvl-online_contact-information-changes";

    private static final JsonMapper JSON = JsonMapper.shared();

    private static final int MAX_STORED_NUMBER_DIGITS = 131_072 + 16_383;

    private static final JsonMapper EXACT_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNumberLength(MAX_STORED_NUMBER_DIGITS)
                            .build())
                    .build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private OneCFailureMessages() {
    }

    static String digits(int length) {
        return DIGITS.substring(0, length);
    }

    static OneCMessage contragent(boolean sanatorium, String field, String value, InboundStatus expected) {
        ObjectNode data = JSON.createObjectNode()
                .put("guid", CONTRAGENT_ID)
                .put("thisIsLPU", sanatorium)
                .put("deleteMark", false)
                .put("Наименование", CONTRAGENT_NAME)
                .put(field, value);
        if (sanatorium) {
            data.putArray("typesActivities").addObject().put("guidTypeActivities", RVL_ACTIVITY);
        }
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data, expected);
    }

    static OneCMessage contactsWithoutContragent() {
        ObjectNode data = JSON.createObjectNode();
        data.putArray("filter");
        data.putArray("recordSet").addObject()
                .put("type", "Адрес")
                .put("representation", "Стенд: г. Сочи, ул. Курортная, 1");
        return new OneCMessage(OneCStream.LPU_CONTACTS, CONTACTS_QUEUE, data, InboundStatus.FAILED);
    }

    static JsonNode storedNodeOf(String payload, String field) {
        return nodeOf(EXACT_JSON.readTree(payload).path("data"), field);
    }

    static boolean sameValue(JsonNode sent, JsonNode stored) {
        return sent.isNumber() && stored.isNumber()
                ? sent.decimalValue().compareTo(stored.decimalValue()) == 0
                : sent.asString().equals(stored.asString());
    }

    static JsonNode nodeOf(JsonNode data, String field) {
        return data.findValues(field).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("в сообщении нет поля " + field));
    }
}
