package ru.projectx.datareference.mapper.stream;

import static ru.projectx.datareference.mapper.value.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.uuidOrNull;

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
import ru.projectx.datareference.mapper.CatalogReference;
import ru.projectx.datareference.mapper.CatalogReferences;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.OwnedCatalogStreamMapper;
import ru.projectx.datareference.mapper.value.OneCValues;
import ru.projectx.datareference.repository.LpuContractRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class LpuContractMapper extends OwnedCatalogStreamMapper<LpuContract, Lpu, LpuContract.Details> {

    private static final String VALUE = "value";
    private static final String OWNER_ID = "ownerId";
    private static final String BRANCH_ID = "contragentBranchId";

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

    private Optional<String> unmetRvlProperty(JsonNode data) {
        return contract.rvlContract().filter(property -> !extPropertyFlag(data, property));
    }

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

    private static LpuContract.Details detailsOf(ObjectNode data) {
        LocalDate beginDate = beginDateOf(data);
        return new LpuContract.Details(
                textOrNull(data, "number"),
                textOrNull(data, "name"),
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
