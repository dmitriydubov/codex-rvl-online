package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
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
 * ТЗ-10: услуги контрагентов. Фильтр один — {@code ownerId} должен резолвиться в наш
 * договор; тем самым хранятся услуги только по договорам наших санаториев.
 *
 * <p>Это и тот случай, о котором контракт предупреждает отдельно: услуга может приехать
 * <b>раньше своего договора</b>, уйти в пропуск и не появиться до следующего изменения
 * в 1С. Причина пропуска пишется в строку сырья, чтобы такие сообщения можно было найти
 * и переобработать — переводом строки обратно в {@code NEW}.
 */
@Component
public class ContragentServiceMapper implements StreamMapper {

    private final ContragentServiceRepository repository;
    private final LpuContractRepository contractRepository;
    private final Clock clock;

    public ContragentServiceMapper(
            ContragentServiceRepository repository,
            LpuContractRepository contractRepository,
            Clock clock) {

        this.repository = repository;
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
        Optional<LpuContract> contract = contractRepository.findByExternalId(ownerId);
        if (contract.isEmpty()) {
            return ImportResult.skipped("услуга по договору вне справочника: " + ownerId);
        }

        UUID externalId = OneCValues.requiredUuid(data, "id");
        ContragentService.Details details = detailsOf(data);
        Optional<ContragentService> existing = repository.findByExternalId(externalId);

        if (existing.isEmpty()) {
            repository.save(ContragentService.create(externalId, contract.get(), details, clock.instant()));
            return ImportResult.applied();
        }

        ContragentService service = existing.get();
        service.applySnapshot(contract.get(), details, clock.instant());
        repository.save(service);
        return ImportResult.applied();
    }

    private static ContragentService.Details detailsOf(ObjectNode data) {
        return new ContragentService.Details(
                OneCValues.uuid(data, "typeAppealId").orElse(null),
                OneCValues.text(data, "code").orElse(null),
                OneCValues.requiredText(data, "name"),
                OneCValues.flag(data, "deleteMark"));
    }
}
