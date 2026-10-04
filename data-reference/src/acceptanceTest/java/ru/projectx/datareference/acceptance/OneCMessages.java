package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.onec.OneCGuid;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщения 1С:УСК из таблиц шагов «Дано»: ключ таблицы — поле контракта ТЗ-1, ТЗ-2, ТЗ-3
 * и ТЗ-6 по-русски, значение — как его прислала бы 1С.
 *
 * <p>Обязательные поля контракта, которых сценарий не называет, заполняются здесь
 * синтетикой: ФИО и дата рождения полиса (заглушками, см. {@link #policy}), тип продукта ДМС,
 * вид деятельности РВЛ, лимит программы полиса. Два значения — конфигурация контура
 * из {@code application.yml}: без них санаторий и договор не прошли бы фильтры потоков:
 * их расхождение с конфигурацией ловит шаг разбора,
 * который ждёт у каждого сообщения свой исход ({@link OneCMessage#expected()}). Все значения
 * вымышлены.
 *
 * <p>Строка таблицы, которой шаг не знает, и логическое значение не из «true»/«false» —
 * ошибка сценария, а не молча пропущенное или ложное поле.
 */
final class OneCMessages {

    /** {@code projectx.onec.contract.rvl-activity-id} из application.yml. */
    private static final String RVL_ACTIVITY = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    /** {@code projectx.onec.contract.dms-product-type} из application.yml. */
    private static final String DMS_PRODUCT = "Личное страхование";
    private static final String INSURER_ROLE = "Страхователь";
    private static final BigDecimal POLICY_PROGRAM_LIMIT = new BigDecimal("150000.00");
    /** {@code projectx.onec.policy.email-property} — умолчание {@code PolicyContractProperties}. */
    private static final String EMAIL_PROPERTY = "Адрес элпочты застрахованного";
    private static final String PLACEHOLDER_LAST_NAME = "Заглушкин";
    private static final String PLACEHOLDER_FIRST_NAME = "Шаг";
    private static final String PLACEHOLDER_DATE_OF_BIRTH = "1985-06-15";

    /** Строка таблицы шага с явным {@code message-id} сообщения. */
    static final String MESSAGE_ID = "message-id";
    /** Строка таблицы шага о байтах перед конвертом («UTF-8 BOM» или «ничего»); читает её шаг публикации. */
    static final String BEFORE_ENVELOPE = "перед конвертом";

    private static final String CONTRAGENTS_QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String CONTRACTS_QUEUE = "1c-usk_to_rvl-online_contracts-changes";
    private static final String PROGRAMS_QUEUE = "1c-usk_to_rvl-online_programms-changes";
    private static final String POLICIES_QUEUE = "1c-usk_to_rvl-online_policies";

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCMessages() {
    }

    /**
     * ТЗ-3: контрагент без признака ЛПУ — страхователь. Строка в справочнике страхователей
     * создаётся, а сообщение уходит в пропуск с причиной: санаторием оно не стало.
     */
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

    /** ТЗ-3: ЛПУ с видом деятельности РВЛ — санаторий. */
    static OneCMessage sanatorium(Map<String, String> table) {
        onlyKnown(table, "external_id", "наименование");
        ObjectNode data = JSON.createObjectNode()
                .put("guid", required(table, "external_id"))
                .put("thisIsLPU", true)
                .put("deleteMark", false)
                .put("Наименование", required(table, "наименование"));
        data.putArray("typesActivities").addObject().put("guidTypeActivities", RVL_ACTIVITY);
        return new OneCMessage(OneCStream.CONTRAGENTS, CONTRAGENTS_QUEUE, data, InboundStatus.PROCESSED);
    }

    /**
     * ТЗ-2: договор страхования продукта ДМС; страхователь — если сценарий его назвал. Строки
     * «message-id» и «перед конвертом» — свойства доставки, а не договора: первая задаёт идентификатор
     * сообщения явно, вторую читает шаг публикации в брокер.
     */
    static OneCMessage insuranceContract(Map<String, String> table) {
        onlyKnown(table, "external_id", "номер", "страхователь", "начало", "окончание", MESSAGE_ID, BEFORE_ENVELOPE);
        ObjectNode data = JSON.createObjectNode()
                .put("contractId", required(table, "external_id"))
                .put("number", required(table, "номер"))
                .put("productType", DMS_PRODUCT)
                .put("dateBeginISO", required(table, "начало"))
                .put("dateEndISO", required(table, "окончание"))
                .put("notActive", false);
        Optional.ofNullable(table.get("страхователь")).ifPresent(insurer -> addInsurer(data, insurer));
        return new OneCMessage(OneCStream.INSURANCE_CONTRACTS, CONTRACTS_QUEUE, data, InboundStatus.PROCESSED,
                fixedMessageId(table));
    }

    /**
     * {@code message-id} из таблицы шага — только канонический GUID: иное значение — ошибка сценария, а не
     * сообщение с испорченным конвертом (тот путь закрепляют тесты приёма).
     */
    private static @Nullable UUID fixedMessageId(Map<String, String> table) {
        return Optional.ofNullable(table.get(MESSAGE_ID))
                .map(raw -> OneCGuid.parse(raw).orElseThrow(() -> new IllegalArgumentException(
                        "message-id в таблице шага — не канонический GUID: " + raw)))
                .orElse(null);
    }

    /** ТЗ-6: программа РВЛ с одной строкой перечня {@code service[]}. */
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

    /**
     * ТЗ-1: полис со сроком действия; программа полиса, отчество, пол, e-mail и пометка удаления —
     * если сценарий их назвал. Фамилия, имя и дата рождения обязательны в контракте: не названные
     * сценарием заполняются заглушками.
     */
    static OneCMessage policy(Map<String, String> table) {
        onlyKnown(table, "external_id", "номер", "договор", "программа", "начало", "окончание",
                "фамилия", "имя", "отчество", "дата рождения", "пол", "e-mail", "пометка удаления");
        ObjectNode data = JSON.createObjectNode()
                .put("id", required(table, "external_id"))
                .put("idContract", required(table, "договор"))
                .put("number", required(table, "номер"))
                .put("deleteMark", table.containsKey("пометка удаления") && flag(table, "пометка удаления"));
        addInsured(data, table);
        data.putArray("header").addObject()
                .put("rowNumber", 1)
                .put("beginDate", required(table, "начало"))
                .put("endDate", required(table, "окончание"))
                .put("availability", true);
        Optional.ofNullable(table.get("программа")).ifPresent(program -> addPolicyProgram(data, program, table));
        return new OneCMessage(OneCStream.POLICIES, POLICIES_QUEUE, data, InboundStatus.PROCESSED);
    }

    /**
     * Застрахованный полиса. Заглушки нарочно не совпадают со значениями сценариев: шаг,
     * который забыл бы взять ФИО из таблицы, иначе выглядел бы как правильный. Почта в ТЗ-1 —
     * не поле, а дополнительное свойство полиса с именем из конфигурации контура.
     */
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

    private static void onlyKnown(Map<String, String> table, String... keys) {
        Set<String> unknown = new TreeSet<>(table.keySet());
        unknown.removeAll(Set.of(keys));
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("в таблице шага строки, которых шаг не знает: " + unknown);
        }
    }

    private static boolean flag(Map<String, String> table, String key) {
        String value = required(table, key);
        if (!Set.of("true", "false").contains(value)) {
            throw new IllegalArgumentException("строка «" + key + "» — не true и не false: " + value);
        }
        return Boolean.parseBoolean(value);
    }

    private static String required(Map<String, String> table, String key) {
        return Optional.ofNullable(table.get(key))
                .orElseThrow(() -> new IllegalArgumentException("в таблице шага нет строки «" + key + "»"));
    }
}
