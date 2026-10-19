package ru.projectx.datareference.mapper.stream;

import static ru.projectx.datareference.mapper.value.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.textOrNull;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.CatalogStreamMapper;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.value.OneCValues;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class InsuranceContractMapper extends CatalogStreamMapper<InsuranceContract, InsuranceContract.Details> {

    private static final String DMS_PRODUCT_NOT_SET =
            "не задан projectx.onec.contract.dms-product-type — отличить ДМС от прочих продуктов страхования нечем";

    private final OneCContractProperties contract;

    public InsuranceContractMapper(
            InsuranceContractRepository repository, OneCContractProperties contract, Clock clock) {

        super(OneCStream.INSURANCE_CONTRACTS, repository::findByExternalId, InsuranceContract::create,
                InsuranceContract::applySnapshot, repository::save, clock);
        this.contract = contract;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        List<String> dmsProducts = contract.dmsProducts();
        if (dmsProducts.isEmpty()) {
            return ImportResult.skipped(DMS_PRODUCT_NOT_SET);
        }
        return applyIfDms(dmsProducts, data);
    }

    private ImportResult applyIfDms(List<String> dmsProducts, ObjectNode data) {
        String productType = textOrNull(data, "productType");
        if (!dmsProducts.contains(productType)) {
            return ImportResult.skipped("договор не ДМС: тип продукта " + productType);
        }
        return upsert(OneCValues.requiredUuid(data, "contractId"), detailsOf(data));
    }

    private InsuranceContract.Details detailsOf(ObjectNode data) {
        return new InsuranceContract.Details(
                OneCValues.requiredText(data, "number"),
                OneCValues.requiredDate(data, "dateBeginISO"),
                dateOrNull(data, "dateEndISO"),
                insurerOf(data).orElse(null),
                OneCValues.flag(data, "notActive"));
    }

    private Optional<UUID> insurerOf(JsonNode data) {
        return StreamSupport.stream(data.path("contragentList").spliterator(), false)
                .filter(this::isInsurer)
                .findFirst()
                .flatMap(party -> OneCValues.uuid(party.path("contragent"), "guid"));
    }

    private boolean isInsurer(JsonNode party) {
        return StreamSupport.stream(party.path("rolesList").spliterator(), false)
                .anyMatch(role -> contract.insurerRole().equals(role.path("roleId").asString(null)));
    }
}
