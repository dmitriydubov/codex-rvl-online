package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.integerOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.Mkb10;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.service.onec.OneCValues;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает справочник диагнозов МКБ-10 из 1С:УСК (ТЗ-4).
 *
 * <p>Справочник принимается целиком, без фильтра. Каждое сообщение создаёт диагноз или заменяет его
 * реквизиты: версий у сообщений нет, и действует последнее полученное.
 */
@Component
public class Mkb10Mapper extends CatalogMapper<Mkb10, Mkb10.Details> {

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
