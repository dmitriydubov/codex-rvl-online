package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.LpuContractRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает договоры санаториев из 1С:УСК (ТЗ-5) и хранит ровно то, что прислала 1С: прайсовый договор выбирается
 * при расчёте цены, а правило «один действующий основной договор» не поддерживается, иначе пришлось бы менять записи,
 * о которых сообщения не было. Договор принадлежит отделению, если оно указано и известно, иначе головной организации.
 */
@Component
public class LpuContractMapper extends OwnedCatalogStreamMapper<LpuContract, Lpu, LpuContract.Details> {

    private static final String VALUE = "value";
    private static final String OWNER_ID = "ownerId";
    private static final String BRANCH_ID = "contragentBranchId";

    /** Константа, а не настройка, как у признака договора РВЛ: это имя контракт 1С называет прямо. */
    private static final String SOB_EXPORT_PROPERTY = "Выгружать прейскуранты в систему бронирования";

    private final CatalogReference<Lpu> sanatoriums;
    private final OneCContractProperties contract;

    public LpuContractMapper(
            LpuContractRepository repository,
            CatalogReferences references,
            OneCContractProperties contract,
            Clock clock) {

        super(OneCStream.LPU_CONTRACTS, repository::findByExternalId, LpuContract::create,
                LpuContract::applySnapshot, repository::save, clock, ParentKind.LPU_CONTRACT, references);
        this.sanatoriums = references.sanatoriums("договор не нашего контрагента: ");
        this.contract = contract;
    }

    @Override
    protected ImportResult decide(ObjectNode data) {
        return unmetRvlProperty(data)
                .map(property -> ImportResult.skipped("договор без признака " + property))
                .orElseGet(() -> applyToSanatorium(data));
    }

    /**
     * Имя признака договора на восстановительное лечение, если в договоре он не взведён. Имя задаётся настройкой,
     * потому что в контракте 1С на его месте пока заглушка; пока имя не задано, проходит любой договор.
     */
    private Optional<String> unmetRvlProperty(JsonNode data) {
        return contract.rvlContract().filter(property -> !extPropertyFlag(data, property));
    }

    /**
     * Отделение у нас отдельный санаторий и важнее головной организации. Неизвестное отделение договор ждёт, даже
     * если головная организация уже есть; решает она, только если отделение не указано или признано не нашим.
     */
    private ImportResult applyToSanatorium(ObjectNode data) {
        return OneCValues.uuid(data, BRANCH_ID)
                .map(branch -> sanatoriums.applyIfKnown(branch, sanatorium -> applyUnder(sanatorium, data),
                        () -> applyToOwner(data)))
                .orElseGet(() -> applyToOwner(data));
    }

    private ImportResult applyToOwner(ObjectNode data) {
        Supplier<ImportResult> stranger = () -> stranger(data);
        return OneCValues.uuid(data, OWNER_ID)
                .map(owner -> sanatoriums.applyIfKnown(owner, sanatorium -> applyUnder(sanatorium, data), stranger))
                .orElseGet(stranger);
    }

    private ImportResult applyUnder(Lpu sanatorium, ObjectNode data) {
        return upsertUnder(sanatorium, OneCValues.requiredUuid(data, "id"), detailsOf(data));
    }

    private static ImportResult stranger(JsonNode data) {
        return ImportResult.skipped("договор не нашего контрагента: ownerId=" + uuidOrNull(data, OWNER_ID)
                + ", contragentBranchId=" + uuidOrNull(data, BRANCH_ID));
    }

    /** Дата начала читается первой: без неё договор нарушает контракт, что бы ни было в остальных полях. */
    private static LpuContract.Details detailsOf(ObjectNode data) {
        LocalDate beginDate = beginDateOf(data);
        return new LpuContract.Details(
                textOrNull(data, "number"),
                textOrNull(data, "name"),
                // Нулевой GUID основного договора значит «договор сам по себе» и становится пустой ссылкой.
                uuidOrNull(data, "mainContractId"),
                OneCValues.flag(data, "usePricesMainAgreement"),
                extPropertyFlag(data, SOB_EXPORT_PROPERTY),
                beginDate,
                dateOrNull(data, "endDate"),
                OneCValues.flag(data, "deleteMark"));
    }

    private static LocalDate beginDateOf(JsonNode data) {
        return OneCValues.date(data, "beginDate")
                .orElseThrow(() -> new OneCFieldException("у договора ЛПУ нет даты начала действия"));
    }

    private static boolean extPropertyFlag(JsonNode data, String propertyName) {
        return StreamSupport.stream(data.path("extProperties").spliterator(), false)
                .filter(row -> propertyName.equals(row.path("propertyName").asString(null)))
                .findFirst()
                .map(row -> row.path(VALUE).path(VALUE).asBoolean(false))
                .orElse(false);
    }
}
