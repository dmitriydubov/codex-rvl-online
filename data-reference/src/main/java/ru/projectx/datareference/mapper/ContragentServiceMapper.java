package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает услуги санаториев из 1С:УСК (ТЗ-10). Услуга оказывается по договору с санаторием, и
 * принимаются только услуги по договорам, которые уже есть в справочнике: так хранятся услуги
 * только наших санаториев.
 *
 * <p>Услуга может прийти раньше своего договора. Тогда сообщение уходит в пропуск с причиной,
 * в которой назван GUID договора, и услуга не появится до следующего её изменения в 1С. Такие
 * сообщения находятся по причине пропуска и обрабатываются заново, когда их строку сырья
 * возвращают в статус {@code NEW}.
 *
 * <p>Версий у сообщений нет, поэтому известная услуга получает реквизиты последнего сообщения
 * вместе с договором.
 */
@Component
public class ContragentServiceMapper
        extends OwnedCatalogStreamMapper<ContragentService, LpuContract, ContragentService.Details> {

    private final CatalogReference<LpuContract> contracts;

    public ContragentServiceMapper(
            ContragentServiceRepository repository,
            LpuContractRepository contractRepository,
            Clock clock) {

        super(OneCStream.CONTRAGENT_SERVICES, repository::findByExternalId, ContragentService::create,
                ContragentService::applySnapshot, repository::save, clock);
        this.contracts = new CatalogReference<>(contractRepository::findByExternalId,
                "услуга по договору вне справочника: ");
    }

    @Override
    public ImportResult apply(ObjectNode data) {
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
