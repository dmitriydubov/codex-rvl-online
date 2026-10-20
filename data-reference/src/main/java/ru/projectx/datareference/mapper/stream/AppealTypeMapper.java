package ru.projectx.datareference.mapper.stream;

import static ru.projectx.datareference.mapper.value.OptionalFields.integerOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.uuidOrNull;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.catalog.AppealType;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.mapper.UnfilteredStreamMapper;
import ru.projectx.datareference.mapper.value.OneCValues;
import ru.projectx.datareference.repository.catalog.AppealTypeRepository;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AppealTypeMapper extends UnfilteredStreamMapper<AppealType, AppealType.Details> {

    public AppealTypeMapper(AppealTypeRepository repository, Clock clock) {
        super(OneCStream.APPEAL_TYPES, repository::findByExternalId,
                AppealType::create, AppealType::applySnapshot, repository::save, clock);
    }

    @Override
    protected AppealType.Details snapshotOf(ObjectNode data) {
        return new AppealType.Details(
                uuidOrNull(data, "idParent"),
                textOrNull(data, "code"),
                OneCValues.requiredText(data, "name"),
                textOrNull(data, "category"),
                textOrNull(data, "systemCode"),
                integerOrNull(data, "hierarchyLevel"),
                OneCValues.flag(data, "directAttachment"),
                OneCValues.flag(data, "indirectAttachment"),
                OneCValues.flag(data, "deleteMark"));
    }
}
