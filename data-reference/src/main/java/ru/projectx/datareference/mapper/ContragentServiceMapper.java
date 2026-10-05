package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
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
public class ContragentServiceMapper implements StreamMapper {

    private static final String OUTSIDE_CATALOG = "услуга по договору вне справочника: ";

    private final SnapshotUpsert<ContragentService, ServiceUnderContract> upsert;
    private final LpuContractRepository contractRepository;
    private final Clock clock;

    public ContragentServiceMapper(
            ContragentServiceRepository repository,
            LpuContractRepository contractRepository,
            Clock clock) {

        this.upsert = new SnapshotUpsert<>(repository::findByExternalId, ContragentServiceMapper::create,
                ContragentServiceMapper::refresh, repository::save);
        this.contractRepository = contractRepository;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.CONTRAGENT_SERVICES;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID ownerId = OneCValues.requiredUuid(data, "ownerId");
        return contractRepository.findByExternalId(ownerId)
                .map(contract -> applyUnder(contract, data))
                .orElseGet(() -> ImportResult.skipped(OUTSIDE_CATALOG + ownerId));
    }

    private ImportResult applyUnder(LpuContract contract, ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "id");
        return upsert.apply(externalId, new ServiceUnderContract(contract, detailsOf(data)), clock.instant());
    }

    private static ContragentService create(UUID externalId, ServiceUnderContract offered, Instant now) {
        return ContragentService.create(externalId, offered.contract(), offered.details(), now);
    }

    private static void refresh(ContragentService service, ServiceUnderContract offered, Instant now) {
        service.applySnapshot(offered.contract(), offered.details(), now);
    }

    private static ContragentService.Details detailsOf(ObjectNode data) {
        return new ContragentService.Details(
                uuidOrNull(data, "typeAppealId"),
                textOrNull(data, "code"),
                OneCValues.requiredText(data, "name"),
                OneCValues.flag(data, "deleteMark"));
    }

    /** Договор, по которому оказывается услуга, и реквизиты услуги из одного сообщения. */
    private record ServiceUnderContract(LpuContract contract, ContragentService.Details details) {
    }
}
