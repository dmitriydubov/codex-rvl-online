package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает услуги санаториев из 1С:УСК; принимаются только услуги по договорам из справочника. Услуга чужого договора
 * запоминается как не наша, чтобы прайс по ней тоже пропускался, а не ждал её.
 */
@Component
public class ContragentServiceMapper
        extends OwnedCatalogStreamMapper<ContragentService, LpuContract, ContragentService.Details> {

    private final CatalogReference<LpuContract> contracts;

    public ContragentServiceMapper(
            ContragentServiceRepository repository,
            CatalogReferences references,
            Clock clock) {

        super(OneCStream.CONTRAGENT_SERVICES, repository::findByExternalId, ContragentService::create,
                ContragentService::applySnapshot, repository::save, clock, ParentKind.CONTRAGENT_SERVICE, references);
        this.contracts = references.contracts("услуга по договору вне справочника: ");
    }

    @Override
    protected ImportResult decide(ObjectNode data) {
        return contracts.applyIfKnown(OneCValues.requiredUuid(data, "ownerId"), contract -> applyUnder(contract, data));
    }

    private ImportResult applyUnder(LpuContract contract, ObjectNode data) {
        return upsertUnder(contract, OneCValues.requiredUuid(data, "id"), detailsOf(data));
    }

    private static ContragentService.Details detailsOf(ObjectNode data) {
        return new ContragentService.Details(
                uuidOrNull(data, "typeAppealId"),
                textOrNull(data, "code"),
                OneCValues.requiredText(data, "name"),
                OneCValues.flag(data, "deleteMark"));
    }
}
