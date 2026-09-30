package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.AppealTypeRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.node.ObjectNode;

/** ТЗ-7: виды обращений целиком. Фильтра нет, есть контроль версии. */
@Component
public class AppealTypeMapper implements StreamMapper {

    private final AppealTypeRepository repository;
    private final Clock clock;

    public AppealTypeMapper(AppealTypeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.APPEAL_TYPES;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "id");
        AppealType.Details details = detailsOf(data);
        Optional<AppealType> existing = repository.findByExternalId(externalId);

        if (existing.isEmpty()) {
            repository.save(AppealType.create(externalId, details, clock.instant()));
            return ImportResult.applied();
        }

        AppealType appealType = existing.get();
        appealType.applySnapshot(details, clock.instant());
        repository.save(appealType);
        return ImportResult.applied();
    }

    private static AppealType.Details detailsOf(ObjectNode data) {
        return new AppealType.Details(
                OneCValues.uuid(data, "idParent").orElse(null),
                OneCValues.text(data, "code").orElse(null),
                OneCValues.requiredText(data, "name"),
                OneCValues.text(data, "category").orElse(null),
                OneCValues.text(data, "systemCode").orElse(null),
                OneCValues.integer(data, "hierarchyLevel").orElse(null),
                OneCValues.flag(data, "directAttachment"),
                OneCValues.flag(data, "indirectAttachment"),
                OneCValues.flag(data, "deleteMark"));
    }
}
