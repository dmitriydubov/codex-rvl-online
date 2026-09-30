package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.Mkb10;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.node.ObjectNode;

/** ТЗ-4: справочник МКБ-10 целиком. Фильтра нет, есть контроль версии. */
@Component
public class Mkb10Mapper implements StreamMapper {

    private final Mkb10Repository repository;
    private final Clock clock;

    public Mkb10Mapper(Mkb10Repository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.MKB10;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "id");
        Mkb10.Details details = detailsOf(data);
        Optional<Mkb10> existing = repository.findByExternalId(externalId);

        if (existing.isEmpty()) {
            repository.save(Mkb10.create(externalId, details, clock.instant()));
            return ImportResult.applied();
        }

        Mkb10 code = existing.get();
        code.applySnapshot(details, clock.instant());
        repository.save(code);
        return ImportResult.applied();
    }

    private static Mkb10.Details detailsOf(ObjectNode data) {
        return new Mkb10.Details(
                OneCValues.uuid(data, "parentId").orElse(null),
                OneCValues.requiredText(data, "code"),
                OneCValues.requiredText(data, "name"),
                OneCValues.text(data, "systemCode").orElse(null),
                OneCValues.integer(data, "hierarchyLevels").orElse(null),
                OneCValues.flag(data, "deleteMark"));
    }
}
