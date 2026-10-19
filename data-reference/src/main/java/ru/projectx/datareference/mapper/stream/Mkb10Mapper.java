package ru.projectx.datareference.mapper.stream;

import static ru.projectx.datareference.mapper.value.OptionalFields.integerOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.uuidOrNull;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.catalog.Mkb10;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.UnfilteredStreamMapper;
import ru.projectx.datareference.mapper.value.OneCValues;
import ru.projectx.datareference.repository.Mkb10Repository;
import tools.jackson.databind.node.ObjectNode;

@Component
public class Mkb10Mapper extends UnfilteredStreamMapper<Mkb10, Mkb10.Details> {

    public Mkb10Mapper(Mkb10Repository repository, Clock clock) {
        super(OneCStream.MKB10, repository::findByExternalId,
                Mkb10::create, Mkb10::applySnapshot, repository::save, clock);
    }

    @Override
    protected Mkb10.Details snapshotOf(ObjectNode data) {
        return new Mkb10.Details(
                uuidOrNull(data, "parentId"),
                OneCValues.requiredText(data, "code"),
                OneCValues.requiredText(data, "name"),
                textOrNull(data, "systemCode"),
                integerOrNull(data, "hierarchyLevels"),
                OneCValues.flag(data, "deleteMark"));
    }
}
