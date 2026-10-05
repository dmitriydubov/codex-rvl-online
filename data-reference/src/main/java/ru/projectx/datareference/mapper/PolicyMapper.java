package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.amountOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.PolicyContractProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.service.onec.OneCValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает полис застрахованного из 1С:УСК (ТЗ-1) вместе с его программами и условиями
 * обслуживания. Фильтра нет: принимаются все полисы, и последнее полученное сообщение
 * целиком заменяет сохранённый полис и обе его табличные части.
 *
 * <p>Две особенности потока не сводятся к переносу полей:
 * <ul>
 *   <li>срок действия полиса берётся не из полиса, а из одной из строк шапки, которую
 *       выбирает {@link #actualHeaderRow};</li>
 *   <li>адрес почты лежит в наборе дополнительных свойств «имя — значение», а нужное имя
 *       задаётся настройкой.</li>
 * </ul>
 *
 * <p>Ничего из разобранного не попадает ни в журнал, ни в текст исключения. Здесь проходят ФИО,
 * дата рождения и почта, то есть персональные данные (ПДн). Сообщение об ошибке называет поле,
 * но не значение, а разбирать проблемный случай нужно по строке сырья в базе.
 */
@Component
public class PolicyMapper extends CatalogMapper<Policy, Policy.Snapshot> {

    private static final String HEADER = "header";
    private static final String PROGRAMS = "programms";
    private static final String SERVICES = "services";
    private static final String EXT_PROPERTIES = "extProperties";
    private static final String PROPERTY_NAME = "propertyName";
    private static final String VALUE = "value";
    private static final String ROW_NUMBER = "rowNumber";
    private static final String AVAILABILITY = "availability";
    private static final String BEGIN_DATE = "beginDate";
    private static final String END_DATE = "endDate";
    private static final String PROGRAM_ID = "idProgramm";

    private final PolicyContractProperties contract;

    public PolicyMapper(PolicyRepository repository, PolicyContractProperties contract, Clock clock) {
        super(OneCStream.POLICIES, repository::findByExternalId,
                Policy::create, Policy::applySnapshot, repository::save, clock);
        this.contract = contract;
    }

    @Override
    protected Policy.Snapshot snapshotOf(ObjectNode data) {
        return new Policy.Snapshot(
                detailsOf(data),
                rowsOf(data, PROGRAMS).map(PolicyMapper::programOf).toList(),
                rowsOf(data, SERVICES).map(PolicyMapper::serviceLineOf).toList());
    }

    private Policy.Details detailsOf(ObjectNode data) {
        JsonNode header = actualHeaderRow(data);
        return new Policy.Details(
                OneCValues.requiredUuid(data, "idContract"),
                OneCValues.requiredText(data, "number"),
                OneCValues.requiredText(data, "lastName"),
                OneCValues.requiredText(data, "firstName"),
                textOrNull(data, "middleName"),
                OneCValues.requiredDate(data, "dateOfBirth"),
                textOrNull(data, "gender"),
                email(data).orElse(null),
                OneCValues.requiredDate(header, BEGIN_DATE),
                dateOrNull(header, END_DATE),
                OneCValues.flag(header, AVAILABILITY),
                OneCValues.flag(data, "deleteMark"));
    }

    /**
     * Актуальная строка шапки: строка с наибольшим номером среди действующих, а если действующих
     * нет, строка с наибольшим номером вообще.
     *
     * <p>Второе правило описывает не ошибку, а нормальное состояние. У прекращённого полиса
     * действующих строк нет, а срок его действия всё равно нужно знать. Первая попавшаяся строка
     * показала бы человеку чужой период.
     */
    private static JsonNode actualHeaderRow(ObjectNode data) {
        List<JsonNode> rows = rowsOf(data, HEADER).toList();
        return rows.stream()
                .filter(row -> OneCValues.flag(row, AVAILABILITY))
                .max(Comparator.comparingInt(PolicyMapper::rowNumber))
                .or(() -> rows.stream().max(Comparator.comparingInt(PolicyMapper::rowNumber)))
                .orElseThrow(() -> new OneCFieldException(
                        "в сообщении нет ни одной строки header — срок действия полиса брать неоткуда"));
    }

    /**
     * Адрес почты ищется среди дополнительных свойств по имени из настройки. Отсутствие свойства
     * не ошибка: почты у застрахованного может не быть, и тогда он просто не войдёт в личный кабинет.
     */
    private Optional<String> email(ObjectNode data) {
        return rowsOf(data, EXT_PROPERTIES)
                .filter(property -> contract.emailProperty()
                        .equals(OneCValues.text(property, PROPERTY_NAME).orElse(null)))
                .findFirst()
                .flatMap(property -> OneCValues.text(property.path(VALUE), VALUE));
    }

    private static PolicyProgram.Row programOf(JsonNode row) {
        return new PolicyProgram.Row(
                OneCValues.requiredUuid(row, PROGRAM_ID),
                OneCValues.requiredDate(row, BEGIN_DATE),
                dateOrNull(row, END_DATE),
                amountOrNull(row, "amount"),
                amountOrNull(row, "amountD"),
                amountOrNull(row, "premium"),
                amountOrNull(row, "premiumD"),
                OneCValues.amount(row, "limit")
                        .orElseThrow(() -> new OneCFieldException("в строке программы нет лимита")),
                OneCValues.flag(row, AVAILABILITY));
    }

    private static PolicyServiceLine.Row serviceLineOf(JsonNode row) {
        return new PolicyServiceLine.Row(
                uuidOrNull(row, PROGRAM_ID),
                OneCValues.integer(row, "layerNumber")
                        .orElseThrow(() -> new OneCFieldException("в строке условий нет номера слоя")),
                OneCValues.amount(row, "copayment")
                        .orElseThrow(() -> new OneCFieldException("в строке условий нет процента франшизы")),
                dateOrNull(row, BEGIN_DATE),
                dateOrNull(row, END_DATE),
                OneCValues.flag(row, AVAILABILITY));
    }

    /** Порядок строк в базе не хранится: табличная часть замещается целиком. */
    private static Stream<JsonNode> rowsOf(ObjectNode data, String field) {
        return StreamSupport.stream(data.path(field).spliterator(), false);
    }

    private static int rowNumber(JsonNode row) {
        return OneCValues.integer(row, ROW_NUMBER).orElse(0);
    }
}
