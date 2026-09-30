package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщения 1С:УСК из таблиц шагов «Дано»: ключ таблицы — поле контракта ТЗ-1, ТЗ-2, ТЗ-3
 * и ТЗ-6 по-русски, значение — как его прислала бы 1С.
 *
 * <p>Обязательные поля контракта, которых сценарий не называет, заполняются здесь
 * синтетикой, как на стенде потребителя ({@code src/test/resources/stand/seed.sql}):
 * ФИО и дата рождения полиса, тип продукта ДМС, вид деятельности РВЛ, лимит программы
 * полиса. Два значения — конфигурация контура из {@code application.yml}: без них санаторий
 * и договор не прошли бы фильтры потоков. Все значения вымышлены.
 */
final class OneCMessages {

    /** {@code projectx.onec.contract.rvl-activity-id} из application.yml. */
    private static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    /** {@code projectx.onec.contract.dms-product-type} из application.yml. */
    private static final String DMS_PRODUCT = "Личное страхование";
    private static final String INSURER_ROLE = "Страхователь";
    private static final BigDecimal POLICY_PROGRAM_LIMIT = new BigDecimal("150000.00");

    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTRACTS_QUEUE = "1c-usk_to_rvl-online_contracts-changes";
    private static final String PROGRAMS_QUEUE = "1c-usk_to_rvl-online_programms-changes";
    private static final String POLICIES_QUEUE = "1c-usk_to_rvl-online_policies";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCMessages() {
    }

    /** ТЗ-3: контрагент без признака ЛПУ — страхователь. */
    static OneCMessage insurer(Map<String, String> table) {
        ObjectNode data = JSON.createObjectNode()
                .put("guid", required(table, "external_id"))
                .put("thisIsLPU", false)
                .put("deleteMark", false)
                .put("Наименование", required(table, "наименование"))
                .put("НаименованиеПолное", required(table, "полное наименование"));
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data);
    }

    /** ТЗ-3: ЛПУ с видом деятельности РВЛ — санаторий. */
    static OneCMessage sanatorium(Map<String, String> table) {
        ObjectNode data = JSON.createObjectNode()
                .put("guid", required(table, "external_id"))
                .put("thisIsLPU", true)
                .put("deleteMark", false)
                .put("Наименование", required(table, "наименование"));
        data.putArray("typesActivities").addObject().put("guidTypeActivities", RVL_ACTIVITY);
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data);
    }

    /** ТЗ-2: договор страхования продукта ДМС; страхователь — если сценарий его назвал. */
    static OneCMessage insuranceContract(Map<String, String> table) {
        ObjectNode data = JSON.createObjectNode()
                .put("contractId", required(table, "external_id"))
                .put("number", required(table, "номер"))
                .put("productType", DMS_PRODUCT)
                .put("dateBeginISO", required(table, "начало"))
                .put("dateEndISO", required(table, "окончание"))
                .put("notActive", false);
        Optional.ofNullable(table.get("страхователь")).ifPresent(insurer -> addInsurer(data, insurer));
        return new OneCMessage(OneCStream.INSURANCE_CONTRACTS, CONTRACTS_QUEUE, data);
    }

    /** ТЗ-6: программа РВЛ с одной строкой перечня {@code service[]}. */
    static OneCMessage program(Map<String, String> table) {
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, "external_id"))
                .put("rvl", true)
                .put("deleteMark", false)
                .put("name", required(table, "наименование"));
        data.putArray("service").addObject()
                .put("idLPU", required(table, "услуга: санаторий"))
                .put("idTreatment", required(table, "услуга: вид обращения"))
                .put("beginDate", required(table, "услуга: начало"))
                .put("endDate", required(table, "услуга: окончание"))
                .put("numberLayer", Integer.parseInt(required(table, "услуга: слой")))
                .put("directAttachment", Boolean.parseBoolean(required(table, "услуга: прямое прикрепление")))
                .put("percentCopayment", new BigDecimal(required(table, "услуга: процент сооплаты")))
                .put("r", Boolean.parseBoolean(required(table, "услуга: рисковая часть")))
                .put("d", Boolean.parseBoolean(required(table, "услуга: депозитная часть")));
        return new OneCMessage(OneCStream.PROGRAMS, PROGRAMS_QUEUE, data);
    }

    /** ТЗ-1: полис со сроком действия; программа полиса — если сценарий её назвал. */
    static OneCMessage policy(Map<String, String> table) {
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, "external_id"))
                .put("idContract", required(table, "договор"))
                .put("number", required(table, "номер"))
                .put("deleteMark", false)
                .put("lastName", "Тестов")
                .put("firstName", "Тест")
                .put("dateOfBirth", "1990-01-01");
        data.putArray("header").addObject()
                .put("rowNumber", 1)
                .put("beginDate", required(table, "начало"))
                .put("endDate", required(table, "окончание"))
                .put("availability", true);
        Optional.ofNullable(table.get("программа")).ifPresent(program -> addPolicyProgram(data, program, table));
        return new OneCMessage(OneCStream.POLICIES, POLICIES_QUEUE, data);
    }

    private static void addInsurer(ObjectNode contract, String insurer) {
        ObjectNode party = contract.putArray("contragentList").addObject();
        party.putArray("rolesList").addObject()
                .put("roleName", INSURER_ROLE)
                .put("roleId", INSURER_ROLE);
        party.putObject("contragent")
                .put("type", "Юридическое лицо")
                .put("guid", insurer);
    }

    private static void addPolicyProgram(ObjectNode policy, String program, Map<String, String> table) {
        policy.putArray("programms").addObject()
                .put("idProgramm", program)
                .put("beginDate", required(table, "начало"))
                .put("endDate", required(table, "окончание"))
                .put("limit", POLICY_PROGRAM_LIMIT)
                .put("availability", true);
    }

    private static String required(Map<String, String> table, String key) {
        return Optional.ofNullable(table.get(key))
                .orElseThrow(() -> new IllegalArgumentException("в таблице шага нет строки «" + key + "»"));
    }
}
