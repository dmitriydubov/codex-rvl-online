package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class InsuranceContractMapper extends CatalogStreamMapper<InsuranceContract, InsuranceContract.Details> {

    private static final String DMS_PRODUCT_NOT_SET =
            "не задан projectx.onec.contract.dms-product-type — отличить ДМС от прочих продуктов страхования нечем";

    private static final String INSURER_ROLE = "Страхователь";

    private final OneCContractProperties contract;

    public InsuranceContractMapper(
            InsuranceContractRepository repository, OneCContractProperties contract, Clock clock) {

        super(OneCStream.INSURANCE_CONTRACTS, repository::findByExternalId, InsuranceContract::create,
                InsuranceContract::applySnapshot, repository::save, clock);
        this.contract = contract;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        return contract.dmsProduct()
                .map(dmsProduct -> applyIfDms(dmsProduct, data))
                .orElseGet(() -> ImportResult.skipped(DMS_PRODUCT_NOT_SET));
    }

    private ImportResult applyIfDms(String dmsProduct, ObjectNode data) {
        String productType = textOrNull(data, "productType");
        if (!dmsProduct.equals(productType)) {
            return ImportResult.skipped("договор не ДМС: тип продукта " + productType);
        }
        return upsert(OneCValues.requiredUuid(data, "contractId"), detailsOf(data));
    }

    private static InsuranceContract.Details detailsOf(ObjectNode data) {
        return new InsuranceContract.Details(
                OneCValues.requiredText(data, "number"),
                OneCValues.requiredDate(data, "dateBeginISO"),
                dateOrNull(data, "dateEndISO"),
                insurerOf(data).orElse(null),
                OneCValues.flag(data, "notActive"));
    }

    private static Optional<UUID> insurerOf(JsonNode data) {
        return StreamSupport.stream(data.path("contragentList").spliterator(), false)
                .filter(InsuranceContractMapper::isInsurer)
                .findFirst()
                .flatMap(party -> OneCValues.uuid(party.path("contragent"), "guid"));
    }

    private static boolean isInsurer(JsonNode party) {
        return StreamSupport.stream(party.path("rolesList").spliterator(), false)
                .anyMatch(role -> INSURER_ROLE.equals(role.path("roleId").asString(null)));
    }
}
