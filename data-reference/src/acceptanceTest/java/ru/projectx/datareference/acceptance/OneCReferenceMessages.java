package ru.projectx.datareference.acceptance;

import static ru.projectx.datareference.acceptance.StepTables.onlyKnown;
import static ru.projectx.datareference.acceptance.StepTables.required;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщения 1С:УСК справочников каталога ЛПУ и МКБ-10 из таблиц шагов «Дано»: вид обращения (ТЗ-7), код МКБ-10
 * (ТЗ-4), договор ЛПУ (ТЗ-5) и программа РВЛ с перечнем учреждений (ТЗ-6). Ключ таблицы — поле контракта
 * по-русски, значение — как его прислала бы 1С.
 *
 * <p>Поля контракта, которых сценарий не называет, заполняются здесь постоянными значениями. Договор ЛПУ —
 * главный: без ссылки на главный договор и со своими ценами. Строка перечня программы — первый слой рисковой части
 * без франшизы и без прямого прикрепления: строку без типа покрытия разбор отбрасывает, и учреждение пропало бы из
 * каталога не по правилу сценария. Все значения вымышлены.
 */
final class OneCReferenceMessages {

    private static final String EXTERNAL_ID = "external_id";
    private static final String NAME = "наименование";
    private static final String BEGIN = "начало";
    private static final String END = "окончание";
    private static final String SANATORIUM = "санаторий";
    private static final String DELETE_MARK = "deleteMark";
    private static final String PROGRAM_NAME = "Стенд: программа РВЛ";

    private static final String APPEAL_TYPES_QUEUE = "1c-usk_to_rvl-online_types-treatments-changes";
    private static final String MKB10_QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String LPU_CONTRACTS_QUEUE = "1c-usk_to_rvl-online_contragents-contracts-changes";
    private static final String PROGRAMS_QUEUE = "1c-usk_to_rvl-online_programms-changes";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCReferenceMessages() {
    }

    /** Вид обращения (ТЗ-7). Виды обращения РВЛ сервис узнаёт по наименованию из своих настроек. */
    static OneCMessage appealType(Map<String, String> table) {
        onlyKnown(table, EXTERNAL_ID, NAME);
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, EXTERNAL_ID))
                .put("name", required(table, NAME))
                .put(DELETE_MARK, false);
        return new OneCMessage(OneCStream.APPEAL_TYPES, APPEAL_TYPES_QUEUE, data, InboundStatus.PROCESSED);
    }

    /** Код МКБ-10 (ТЗ-4). */
    static OneCMessage mkb10(Map<String, String> table) {
        onlyKnown(table, EXTERNAL_ID, "код", NAME);
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, EXTERNAL_ID))
                .put("code", required(table, "код"))
                .put("name", required(table, NAME))
                .put(DELETE_MARK, false);
        return new OneCMessage(OneCStream.MKB10, MKB10_QUEUE, data, InboundStatus.PROCESSED);
    }

    /** Главный договор санатория со своими ценами (ТЗ-5). Санаторий — владелец договора. */
    static OneCMessage lpuContract(Map<String, String> table) {
        onlyKnown(table, EXTERNAL_ID, "номер", SANATORIUM, BEGIN, END);
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, EXTERNAL_ID))
                .put("number", required(table, "номер"))
                .put("ownerId", required(table, SANATORIUM))
                .put("usePricesMainAgreement", false)
                .put("beginDate", required(table, BEGIN))
                .put("endDate", required(table, END))
                .put(DELETE_MARK, false);
        return new OneCMessage(OneCStream.LPU_CONTRACTS, LPU_CONTRACTS_QUEUE, data, InboundStatus.PROCESSED);
    }

    /** Программа РВЛ с перечнем учреждений (ТЗ-6): строка перечня на каждую строку таблицы шага. */
    static OneCMessage programWithLpuList(String externalId, List<Map<String, String>> lines) {
        ObjectNode data = JSON.createObjectNode()
                .put("id", externalId)
                .put("rvl", true)
                .put(DELETE_MARK, false)
                .put("name", PROGRAM_NAME);
        ArrayNode list = data.putArray("service");
        lines.forEach(line -> addLpuLine(list, line));
        return new OneCMessage(OneCStream.PROGRAMS, PROGRAMS_QUEUE, data, InboundStatus.PROCESSED);
    }

    private static void addLpuLine(ArrayNode list, Map<String, String> line) {
        onlyKnown(line, SANATORIUM, "вид обращения", BEGIN, END);
        list.addObject()
                .put("idLPU", required(line, SANATORIUM))
                .put("idTreatment", required(line, "вид обращения"))
                .put("beginDate", required(line, BEGIN))
                .put("endDate", required(line, END))
                .put("numberLayer", 1)
                .put("directAttachment", false)
                .put("percentCopayment", BigDecimal.ZERO)
                .put("r", true)
                .put("d", false);
    }
}
