package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщения 1С:УСК для сценариев отказа разбора: контрагент с реквизитом заданной длины, контактная информация без
 * GUID контрагента и программа РВЛ с заданным процентом сооплаты. Идентификаторы и наименования выбраны здесь,
 * а не сценарием: сценарий задаёт только то, что проверяет. Все значения вымышлены.
 */
final class OneCFailureMessages {

    private static final String CONTRAGENT_ID = "4e3f2a1b-6c5d-4e7f-8a9b-0c1d2e3f4a5b";
    private static final String CONTRAGENT_NAME = "Стенд: контрагент с реквизитами";
    /** {@code projectx.onec.contract.rvl-activity-id} из application.yml. */
    private static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    private static final String PROGRAM_ID = "5f4e3d2c-1b0a-4c9d-8e7f-6a5b4c3d2e1f";
    private static final String TREATMENT_ID = "6a5b4c3d-2e1f-4a0b-9c8d-7e6f5a4b3c2d";
    /** Цифры реквизита: длина задаётся сценарием, значение — шагом. */
    private static final String DIGITS = "12345678901234567890";

    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTACTS_QUEUE = "1c-usk_to_rvl-online_contact-information-changes";
    private static final String PROGRAMS_QUEUE = "1c-usk_to_rvl-online_programms-changes";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCFailureMessages() {
    }

    /** Цифры реквизита заданной длины. */
    static String digits(int length) {
        return DIGITS.substring(0, length);
    }

    /**
     * Контрагент с одним реквизитом, ИНН или КПП. Санаторий несёт вид деятельности РВЛ и при удаче разбирается
     * в справочник санаториев, страхователь — в справочник страхователей с пропуском.
     *
     * @param sanatorium санаторий РВЛ или страхователь
     * @param field      имя поля реквизита в контракте: «ИНН» или «КПП»
     * @param value      значение поля, каким его присылает 1С:УСК
     * @param expected   исход разбора этого сообщения
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
     * Программа РВЛ с одной строкой перечня учреждений по санаторию {@code sanatoriumId}. Программа одна и та же при
     * любом проценте: следующее сообщение замещает предыдущее.
     */
    static OneCMessage program(String sanatoriumId, BigDecimal percentCopayment, InboundStatus expected) {
        ObjectNode data = JSON.createObjectNode()
                .put("id", PROGRAM_ID)
                .put("rvl", true)
                .put("deleteMark", false)
                .put("name", "Стенд: программа РВЛ с сооплатой");
        data.putArray("service").addObject()
                .put("idLPU", sanatoriumId)
                .put("idTreatment", TREATMENT_ID)
                .put("beginDate", "2026-01-01")
                .put("endDate", "2030-12-31")
                .put("numberLayer", 1)
                .put("directAttachment", false)
                .put("percentCopayment", percentCopayment)
                .put("r", true)
                .put("d", false);
        return new OneCMessage(OneCStream.PROGRAMS, PROGRAMS_QUEUE, data, expected);
    }

    /** Строковое значение поля сообщения, как его прислала 1С:УСК, на любой глубине {@code data}. */
    static String valueOf(OneCMessage message, String field) {
        return message.data().findValues(field).stream()
                .findFirst()
                .map(JsonNode::asString)
                .orElseThrow(() -> new IllegalArgumentException("в сообщении нет поля " + field));
    }

}
