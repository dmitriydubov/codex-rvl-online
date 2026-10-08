package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщения 1С:УСК для сценариев отказа разбора: контрагент с реквизитом заданной длины и контактная информация без
 * GUID контрагента. Идентификаторы и наименования выбраны здесь, а не сценарием: сценарий задаёт только то, что
 * проверяет. Все значения вымышлены.
 */
final class OneCFailureMessages {

    private static final String CONTRAGENT_ID = "4e3f2a1b-6c5d-4e7f-8a9b-0c1d2e3f4a5b";
    private static final String CONTRAGENT_NAME = "Стенд: контрагент с реквизитами";
    /** {@code projectx.onec.contract.rvl-activity-id} из application.yml. */
    private static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    /** Цифры реквизита: длина задаётся сценарием, значение — шагом. */
    private static final String DIGITS = "12345678901234567890";

    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTACTS_QUEUE = "1c-usk_to_rvl-online_contact-information-changes";

    private static final JsonMapper JSON = JsonMapper.shared();

    /**
     * Самое длинное число, которое хранит {@code numeric}: 131072 цифры до запятой и 16383 после. Должно совпадать с
     * пределом, с которым сервис читает сырьё ({@code StoredEnvelope.MAX_STORED_NUMBER_DIGITS}).
     */
    private static final int MAX_STORED_NUMBER_DIGITS = 131_072 + 16_383;

    /** Сырьё читается так же, как его читает разбор: числа — точно и такой длины, какую хранит {@code numeric}. */
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

    /**
     * Контрагент с одним реквизитом («ИНН» или «КПП»). Санаторий несёт вид деятельности РВЛ и при удаче попадает
     * в справочник санаториев, страхователь попадает в справочник страхователей с пропуском.
     */
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

    /** Контактная информация с адресом, но без GUID контрагента ни в фильтре, ни в строке набора. */
    static OneCMessage contactsWithoutContragent() {
        ObjectNode data = JSON.createObjectNode();
        data.putArray("filter");
        data.putArray("recordSet").addObject()
                .put("type", "Адрес")
                .put("representation", "Стенд: г. Сочи, ул. Курортная, 1");
        return new OneCMessage(OneCStream.LPU_CONTACTS, CONTACTS_QUEUE, data, InboundStatus.FAILED);
    }

    /**
     * Значение поля в сырье так, как его отдала база. Число {@code jsonb} хранит по величине, а не по записи:
     * {@code 1E+13} возвращается как {@code 10000000000000}. Дроби читаются в {@link BigDecimal}, без двоичной дроби.
     * Поле ищется в {@code data}, как и в присланном сообщении, а не в метаданных конверта.
     */
    static JsonNode storedNodeOf(String payload, String field) {
        return nodeOf(EXACT_JSON.readTree(payload).path("data"), field);
    }

    /** Присланное и сохранённое значения совпадают: число — по величине, остальное — по тексту. */
    static boolean sameValue(JsonNode sent, JsonNode stored) {
        return sent.isNumber() && stored.isNumber()
                ? sent.decimalValue().compareTo(stored.decimalValue()) == 0
                : sent.asString().equals(stored.asString());
    }

    /** Значение поля сообщения на любой глубине {@code data}. */
    static JsonNode nodeOf(JsonNode data, String field) {
        return data.findValues(field).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("в сообщении нет поля " + field));
    }
}
