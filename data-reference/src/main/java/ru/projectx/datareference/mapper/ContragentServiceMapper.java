package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает услуги санаториев из 1С:УСК (ТЗ-10). Услуга оказывается по договору с санаторием, и
 * принимаются только услуги по договорам, которые уже есть в справочнике: так хранятся услуги
 * только наших санаториев.
 *
 * <p>Услуга договора, признанного не нашим, пропускается и сама запоминается как не наша: прайс по ней
 * тоже пропускается. Услуга, пришедшая раньше своего договора, ждёт его.
 *
 * <p>Версий у сообщений нет, поэтому известная услуга получает реквизиты последнего сообщения
 * вместе с договором.
 */
@Component
public class ContragentServiceMapper
        extends OwnedCatalogStreamMapper<ContragentService, LpuContract, ContragentService.Details> {

    private final CatalogReference<LpuContract> contracts;
    private final ForeignReferences foreign;

    public ContragentServiceMapper(
            ContragentServiceRepository repository,
            LpuContractRepository contractRepository,
            ForeignReferences foreign,
            Clock clock) {

        super(OneCStream.CONTRAGENT_SERVICES, repository::findByExternalId, ContragentService::create,
                ContragentService::applySnapshot, repository::save, clock);
        this.contracts = new CatalogReference<>(contractRepository::findByExternalId, ParentKind.LPU_CONTRACT, foreign,
                "услуга по договору вне справочника: ");
        this.foreign = foreign;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        ImportResult result = contracts.applyIfKnown(
                OneCValues.requiredUuid(data, "ownerId"), contract -> applyUnder(contract, data));
        return foreign.rememberIfSkipped(result, ParentKind.CONTRAGENT_SERVICE, data);
    }

    @Override
    public Optional<UUID> recordOf(ObjectNode data) {
        return OneCValues.uuid(data, "id");
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
