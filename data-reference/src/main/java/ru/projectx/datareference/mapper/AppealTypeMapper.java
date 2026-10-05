package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.integerOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.AppealTypeRepository;
import ru.projectx.datareference.service.onec.OneCValues;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает справочник видов обращения за медицинской помощью из 1С:УСК (ТЗ-7): амбулаторная помощь,
 * стационар, восстановительное лечение и другие.
 *
 * <p>Справочник принимается целиком, без фильтра. Каждое сообщение создаёт вид обращения или заменяет
 * его реквизиты: версий у сообщений нет, и действует последнее полученное.
 */
@Component
public class AppealTypeMapper extends CatalogMapper<AppealType, AppealType.Details> {

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
