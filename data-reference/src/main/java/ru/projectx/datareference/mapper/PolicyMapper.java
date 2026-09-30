package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-1: полис застрахованного. Фильтрации нет — обрабатываются все полисы; единственный
 * отсекающий случай это сообщение не новее сохранённого.
 *
 * <p>Две особенности потока, из-за которых разбор не сводится к упаковке полей:
 * <ul>
 *   <li><b>срок действия берётся не из полиса, а из строки шапки</b> — той, что ТЗ
 *       называет актуальной (см. {@link #actualHeaderRow});</li>
 *   <li><b>адрес почты лежит в наборе дополнительных свойств</b>, а не отдельным полем:
 *       это произвольный набор «имя — значение», и нужное имя задаётся конфигурацией.</li>
 * </ul>
 *
 * <p><b>Ничего из разобранного не попадает ни в лог, ни в текст исключения.</b> Здесь
 * проходят ФИО, дата рождения и почта; сообщения об ошибках называют поле, но не значение,
 * а разбирать проблемный случай нужно по строке сырья в базе.
 */
@Component
public class PolicyMapper implements StreamMapper {

    private static final String HEADER = "header";
    private static final String PROGRAMS = "programms";
    private static final String SERVICES = "services";
    private static final String EXT_PROPERTIES = "extProperties";
    private static final String PROPERTY_NAME = "propertyName";
    private static final String VALUE = "value";
    private static final String ROW_NUMBER = "rowNumber";
    private static final String AVAILABILITY = "availability";

    private final PolicyRepository repository;
    private final PolicyContractProperties contract;
    private final Clock clock;

    public PolicyMapper(PolicyRepository repository, PolicyContractProperties contract, Clock clock) {
        this.repository = repository;
        this.contract = contract;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.POLICIES;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "id");
        Policy.Snapshot snapshot = snapshotOf(data);
        Optional<Policy> existing = repository.findByExternalId(externalId);

        if (existing.isEmpty()) {
            repository.save(Policy.create(externalId, snapshot, clock.instant()));
            return ImportResult.applied();
        }

        Policy policy = existing.get();
        policy.applySnapshot(snapshot, clock.instant());
        repository.save(policy);
        return ImportResult.applied();
    }

    private Policy.Snapshot snapshotOf(ObjectNode data) {
        return new Policy.Snapshot(detailsOf(data), programsOf(data), serviceLinesOf(data));
    }

    private Policy.Details detailsOf(ObjectNode data) {
        JsonNode header = actualHeaderRow(data);
        return new Policy.Details(
                OneCValues.requiredUuid(data, "idContract"),
                OneCValues.requiredText(data, "number"),
                OneCValues.requiredText(data, "lastName"),
                OneCValues.requiredText(data, "firstName"),
                OneCValues.text(data, "middleName").orElse(null),
                OneCValues.requiredDate(data, "dateOfBirth"),
                OneCValues.text(data, "gender").orElse(null),
                email(data).orElse(null),
                OneCValues.requiredDate(header, "beginDate"),
                OneCValues.date(header, "endDate").orElse(null),
                OneCValues.flag(header, AVAILABILITY),
                OneCValues.flag(data, "deleteMark"));
    }

    /**
     * ТЗ-1, п. 1.3: строка с наибольшим номером среди действующих; если действующих нет —
     * строка с наибольшим номером вообще.
     *
     * <p>Второе правило — не запасной вариант на случай ошибки, а описание нормального
     * состояния: у прекращённого полиса действующих строк нет вовсе, а срок его действия
     * всё равно нужно знать. Взять первую попавшуюся строку значило бы показать человеку
     * чужой период.
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
     * Набор дополнительных свойств — произвольные пары «имя — значение», в которых нас
     * интересует ровно одно имя. Отсутствие свойства не ошибка: почты у застрахованного
     * может не быть, и это лишь означает, что в личный кабинет он не войдёт.
     */
    private Optional<String> email(ObjectNode data) {
        return rowsOf(data, EXT_PROPERTIES)
                .filter(property -> contract.emailProperty()
                        .equals(OneCValues.text(property, PROPERTY_NAME).orElse(null)))
                .findFirst()
                .flatMap(property -> OneCValues.text(property.path(VALUE), VALUE));
    }

    private static List<PolicyProgram.Row> programsOf(ObjectNode data) {
        return rowsOf(data, PROGRAMS)
                .map(row -> new PolicyProgram.Row(
                        OneCValues.requiredUuid(row, "idProgramm"),
                        OneCValues.requiredDate(row, "beginDate"),
                        OneCValues.date(row, "endDate").orElse(null),
                        OneCValues.amount(row, "amount").orElse(null),
                        OneCValues.amount(row, "amountD").orElse(null),
                        OneCValues.amount(row, "premium").orElse(null),
                        OneCValues.amount(row, "premiumD").orElse(null),
                        OneCValues.amount(row, "limit")
                                .orElseThrow(() -> new OneCFieldException("в строке программы нет лимита")),
                        OneCValues.flag(row, AVAILABILITY)))
                .toList();
    }

    private static List<PolicyServiceLine.Row> serviceLinesOf(ObjectNode data) {
        return rowsOf(data, SERVICES)
                .map(row -> new PolicyServiceLine.Row(
                        OneCValues.uuid(row, "idProgramm").orElse(null),
                        OneCValues.integer(row, "layerNumber")
                                .orElseThrow(() -> new OneCFieldException("в строке условий нет номера слоя")),
                        OneCValues.amount(row, "copayment")
                                .orElseThrow(() -> new OneCFieldException("в строке условий нет процента франшизы")),
                        OneCValues.date(row, "beginDate").orElse(null),
                        OneCValues.date(row, "endDate").orElse(null),
                        OneCValues.flag(row, AVAILABILITY)))
                .toList();
    }

    /** Порядок строк в базе не хранится: табличная часть замещается целиком. */
    private static Stream<JsonNode> rowsOf(ObjectNode data, String field) {
        return StreamSupport.stream(data.path(field).spliterator(), false);
    }

    private static int rowNumber(JsonNode row) {
        return OneCValues.integer(row, ROW_NUMBER).orElse(0);
    }
}
