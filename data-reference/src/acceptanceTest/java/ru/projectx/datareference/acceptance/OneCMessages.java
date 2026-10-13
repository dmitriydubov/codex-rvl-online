package ru.projectx.datareference.acceptance;

import static ru.projectx.datareference.acceptance.StepTables.flag;
import static ru.projectx.datareference.acceptance.StepTables.flagOr;
import static ru.projectx.datareference.acceptance.StepTables.oneOf;
import static ru.projectx.datareference.acceptance.StepTables.onlyKnown;
import static ru.projectx.datareference.acceptance.StepTables.required;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.OneCGuid;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OneCMessages {

    private static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    private static final String DMS_PRODUCT = "ЛичноеСтрахование";
    private static final Set<String> DEFAULT_DMS_PRODUCTS = Set.of(DMS_PRODUCT, "Личное страхование");
    private static final String INSURER_ROLE = "Страхователь";
    private static final String PARTY = "контрагент";
    private static final String PARTY_ROLE = "роль контрагента";
    private static final Set<String> PARTY_ROLES = Set.of(INSURER_ROLE, "Застрахованный");
    private static final BigDecimal POLICY_PROGRAM_LIMIT = new BigDecimal("150000.00");
    private static final String EMAIL_PROPERTY = "Адрес элпочты застрахованного";
    private static final String PLACEHOLDER_LAST_NAME = "Заглушкин";
    private static final String PLACEHOLDER_FIRST_NAME = "Шаг";
    private static final String PLACEHOLDER_DATE_OF_BIRTH = "1985-06-15";
    private static final String DELETE_MARK = "пометка удаления";
    private static final String BEGIN = "начало";
    private static final String AVAILABILITY = "availability";
    private static final String LINE_PROGRAM = "условие обслуживания: программа";
    private static final String LINE_BEGIN = "условие обслуживания: начало";
    private static final String LINE_END = "условие обслуживания: окончание";
    private static final String LINE_AVAILABILITY = "условие обслуживания: доступность";
    private static final String POLICY_AVAILABILITY = "доступность";

    static final String MESSAGE_ID = "message-id";
    static final String BEFORE_ENVELOPE = "перед конвертом";

    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTRACTS_QUEUE = "1c-usk_to_rvl-online_contracts-changes";
    private static final String PROGRAMS_QUEUE = "1c-usk_to_rvl-online_programms-changes";
    private static final String POLICIES_QUEUE = "1c-usk_to_rvl-online_policies";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCMessages() {
    }

    static OneCMessage insurer(Map<String, String> table) {
        onlyKnown(table, "external_id", "наименование", "полное наименование");
        ObjectNode data = JSON.createObjectNode()
                .put("guid", required(table, "external_id"))
                .put("thisIsLPU", false)
                .put("deleteMark", false)
                .put("Наименование", required(table, "наименование"))
                .put("НаименованиеПолное", required(table, "полное наименование"));
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data, InboundStatus.SKIPPED);
    }

    static OneCMessage sanatorium(Map<String, String> table) {
        onlyKnown(table, "external_id", "наименование", DELETE_MARK);
        ObjectNode data = JSON.createObjectNode()
                .put("guid", required(table, "external_id"))
                .put("thisIsLPU", true)
                .put("deleteMark", flagOr(table, DELETE_MARK, false))
                .put("Наименование", required(table, "наименование"));
        data.putArray("typesActivities").addObject().put("guidTypeActivities", RVL_ACTIVITY);
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data, InboundStatus.PROCESSED);
    }

    static OneCMessage insuranceContract(Map<String, String> table) {
        return insuranceContract(DMS_PRODUCT, InboundStatus.PROCESSED, table);
    }

    static OneCMessage insuranceContractOfProduct(String productType, Map<String, String> table) {
        InboundStatus expected = DEFAULT_DMS_PRODUCTS.contains(productType)
                ? InboundStatus.PROCESSED
                : InboundStatus.SKIPPED;
        return insuranceContract(productType, expected, table);
    }

    private static OneCMessage insuranceContract(String productType, InboundStatus expected, Map<String, String> table) {
        onlyKnown(table, "external_id", "номер", "страхователь", PARTY, PARTY_ROLE, "начало", "окончание",
                MESSAGE_ID, BEFORE_ENVELOPE);
        ObjectNode data = JSON.createObjectNode()
                .put("contractId", required(table, "external_id"))
                .put("number", required(table, "номер"))
                .put("productType", productType)
                .put("dateBeginISO", required(table, "начало"))
                .put("dateEndISO", required(table, "окончание"))
                .put("notActive", false);
        Optional.ofNullable(table.get("страхователь")).ifPresent(insurer -> addParty(data, insurer, INSURER_ROLE));
        Optional.ofNullable(table.get(PARTY))
                .ifPresent(party -> addParty(data, party, oneOf(table, PARTY_ROLE, PARTY_ROLES)));
        return new OneCMessage(OneCStream.INSURANCE_CONTRACTS, CONTRACTS_QUEUE, data, expected, fixedMessageId(table));
    }

    static @Nullable UUID fixedMessageId(Map<String, String> table) {
        return Optional.ofNullable(table.get(MESSAGE_ID))
                .map(raw -> OneCGuid.parse(raw).orElseThrow(() -> new IllegalArgumentException(
                        "message-id в таблице шага — не канонический GUID: " + raw)))
                .orElse(null);
    }

    static OneCMessage program(Map<String, String> table) {
        onlyKnown(table, "external_id", "наименование", "услуга: санаторий", "услуга: вид обращения",
                "услуга: начало", "услуга: окончание", "услуга: слой", "услуга: прямое прикрепление",
                "услуга: процент сооплаты", "услуга: рисковая часть", "услуга: депозитная часть");
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
                .put("directAttachment", flag(table, "услуга: прямое прикрепление"))
                .put("percentCopayment", new BigDecimal(required(table, "услуга: процент сооплаты")))
                .put("r", flag(table, "услуга: рисковая часть"))
                .put("d", flag(table, "услуга: депозитная часть"));
        return new OneCMessage(OneCStream.PROGRAMS, PROGRAMS_QUEUE, data, InboundStatus.PROCESSED);
    }

    static OneCMessage policy(Map<String, String> table) {
        onlyKnown(table, "external_id", "номер", "договор", "программа", BEGIN, "окончание",
                "фамилия", "имя", "отчество", "дата рождения", "пол", "e-mail", DELETE_MARK, POLICY_AVAILABILITY,
                LINE_PROGRAM, LINE_BEGIN, LINE_END, LINE_AVAILABILITY);
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, "external_id"))
                .put("idContract", required(table, "договор"))
                .put("number", required(table, "номер"))
                .put("deleteMark", flagOr(table, DELETE_MARK, false));
        addInsured(data, table);
        data.putArray("header").addObject()
                .put("rowNumber", 1)
                .put("beginDate", required(table, BEGIN))
                .put("endDate", required(table, "окончание"))
                .put(AVAILABILITY, flagOr(table, POLICY_AVAILABILITY, true));
        Optional.ofNullable(table.get("программа")).ifPresent(program -> addPolicyProgram(data, program, table));
        Optional.ofNullable(table.get(LINE_PROGRAM)).ifPresent(program -> addServiceLine(data, program, table));
        return new OneCMessage(OneCStream.POLICIES, POLICIES_QUEUE, data, InboundStatus.PROCESSED);
    }

    private static void addServiceLine(ObjectNode policy, String program, Map<String, String> table) {
        ObjectNode line = policy.putArray("services").addObject()
                .put("idProgramm", program)
                .put("layerNumber", 1)
                .put("copayment", BigDecimal.ZERO)
                .put("beginDate", table.getOrDefault(LINE_BEGIN, required(table, BEGIN)))
                .put(AVAILABILITY, flagOr(table, LINE_AVAILABILITY, true));
        Optional.ofNullable(table.get(LINE_END)).ifPresent(end -> line.put("endDate", end));
    }

    private static void addInsured(ObjectNode policy, Map<String, String> table) {
        policy.put("lastName", table.getOrDefault("фамилия", PLACEHOLDER_LAST_NAME))
                .put("firstName", table.getOrDefault("имя", PLACEHOLDER_FIRST_NAME))
                .put("dateOfBirth", table.getOrDefault("дата рождения", PLACEHOLDER_DATE_OF_BIRTH));
        Optional.ofNullable(table.get("отчество")).ifPresent(middleName -> policy.put("middleName", middleName));
        Optional.ofNullable(table.get("пол")).ifPresent(gender -> policy.put("gender", gender));
        Optional.ofNullable(table.get("e-mail")).ifPresent(email -> policy.putArray("extProperties").addObject()
                .put("propertyName", EMAIL_PROPERTY)
                .putObject("value").put("value", email));
    }

    private static void addParty(ObjectNode contract, String guid, String role) {
        ObjectNode party = contract.putArray("contragentList").addObject();
        party.putArray("rolesList").addObject()
                .put("roleName", role)
                .put("roleId", role);
        party.putObject("contragent")
                .put("type", "Юридическое лицо")
                .put("guid", guid);
    }

    private static void addPolicyProgram(ObjectNode policy, String program, Map<String, String> table) {
        policy.putArray("programms").addObject()
                .put("idProgramm", program)
                .put("beginDate", required(table, BEGIN))
                .put("endDate", required(table, "окончание"))
                .put("limit", POLICY_PROGRAM_LIMIT)
                .put(AVAILABILITY, true);
    }
}
