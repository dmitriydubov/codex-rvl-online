package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-2: договоры страхования. Поток несёт договоры всех продуктов страховщика —
 * от еОСАГО до накопительных, — а нужны нам только ДМС.
 *
 * <p>Фильтр один и жёсткий: {@code productType} должен совпасть со значением контура.
 * Не совпал — пропуск с причиной. Не задан у нас — тоже пропуск, и тоже с причиной:
 * отличить ДМС от чужого продукта в этом случае нечем, а сохранить всё подряд значит
 * завести у себя договоры, к личному кабинету отношения не имеющие.
 *
 * <p>Страхователь берётся из {@code contragentList} — того элемента, у которого в ролях
 * есть «Страхователь». В договоре перечислены и другие стороны, и брать первую попавшуюся
 * нельзя: застрахованный, выгодоприобретатель и страхователь — разные люди.
 */
@Component
public class InsuranceContractMapper implements StreamMapper {

    private static final String CONTRAGENT_LIST = "contragentList";
    private static final String ROLES_LIST = "rolesList";
    private static final String ROLE_ID = "roleId";
    private static final String CONTRAGENT = "contragent";

    /** Значение {@code roleId} у стороны-страхователя. ТЗ-2 называет его прямым текстом. */
    private static final String INSURER_ROLE = "Страхователь";

    private final InsuranceContractRepository repository;
    private final OneCContractProperties contract;
    private final Clock clock;

    public InsuranceContractMapper(
            InsuranceContractRepository repository, OneCContractProperties contract, Clock clock) {

        this.repository = repository;
        this.contract = contract;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.INSURANCE_CONTRACTS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        Optional<String> dmsProduct = contract.dmsProduct();
        if (dmsProduct.isEmpty()) {
            return ImportResult.skipped(
                    "не задан projectx.onec.contract.dms-product-type — отличить ДМС от прочих "
                            + "продуктов страхования нечем");
        }

        String productType = OneCValues.text(data, "productType").orElse(null);
        if (!dmsProduct.get().equals(productType)) {
            return ImportResult.skipped("договор не ДМС: тип продукта " + productType);
        }

        UUID externalId = OneCValues.requiredUuid(data, "contractId");
        InsuranceContract.Details details = detailsOf(data);

        InsuranceContract insuranceContract = repository.findByExternalId(externalId)
                .orElseGet(() -> InsuranceContract.create(externalId, details, clock.instant()));
        insuranceContract.applySnapshot(details, clock.instant());
        repository.save(insuranceContract);
        return ImportResult.applied();
    }

    private static InsuranceContract.Details detailsOf(ObjectNode data) {
        return new InsuranceContract.Details(
                OneCValues.requiredText(data, "number"),
                OneCValues.requiredDate(data, "dateBeginISO"),
                OneCValues.date(data, "dateEndISO").orElse(null),
                insurerOf(data).orElse(null),
                OneCValues.flag(data, "notActive"));
    }

    /**
     * Страхователь — сторона договора с ролью {@link #INSURER_ROLE}. Ролей у одной стороны
     * может быть несколько, поэтому проверяется весь их список, а не первая запись.
     */
    private static Optional<UUID> insurerOf(JsonNode data) {
        return StreamSupport.stream(data.path(CONTRAGENT_LIST).spliterator(), false)
                .filter(InsuranceContractMapper::isInsurer)
                .findFirst()
                .flatMap(party -> OneCValues.uuid(party.path(CONTRAGENT), "guid"));
    }

    private static boolean isInsurer(JsonNode party) {
        return StreamSupport.stream(party.path(ROLES_LIST).spliterator(), false)
                .anyMatch(role -> INSURER_ROLE.equals(role.path(ROLE_ID).asString(null)));
    }
}
