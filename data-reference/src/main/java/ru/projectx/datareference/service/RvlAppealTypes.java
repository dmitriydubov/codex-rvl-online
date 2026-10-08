package ru.projectx.datareference.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.repository.AppealTypeRepository;

/**
 * Виды обращения РВЛ из конфигурации, найденные в справочнике по наименованию; по ним отбираются учреждения и услуги.
 * Переименование в 1С молча обнулит выдачу, поэтому сравнение идёт без регистра и краевых пробелов, а на старте
 * пишется WARN о ненайденных. Кэша нет: запрос дешёвый, а устаревший кэш обошёлся бы дороже.
 */
@Component
public class RvlAppealTypes {

    private static final Logger log = LoggerFactory.getLogger(RvlAppealTypes.class);

    private final AppealTypeRepository appealTypes;
    private final ReferenceApiProperties api;

    public RvlAppealTypes(AppealTypeRepository appealTypes, ReferenceApiProperties api) {
        this.appealTypes = appealTypes;
        this.api = api;
    }

    /** Ключ — GUID 1С, по которому на вид обращения ссылаются перечни программ и услуги. */
    public Map<UUID, AppealType> byExternalId() {
        return found().stream().collect(Collectors.toMap(AppealType::externalId, Function.identity()));
    }

    public Set<UUID> externalIds() {
        return byExternalId().keySet();
    }

    /** Наименования из конфигурации, которых нет в справочнике: по ним каталог и услуги будут пусты. */
    public List<String> missing() {
        Set<String> present = found().stream().map(type -> normalize(type.name())).collect(Collectors.toSet());
        return api.rvlAppealTypes().stream().filter(name -> !present.contains(normalize(name))).toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnIfMissing() {
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            log.warn("виды обращения РВЛ из конфигурации не найдены в appeal_types: {} — "
                    + "R-01 и R-03 по ним отдадут пустую выдачу", missing);
        }
    }

    private List<AppealType> found() {
        Set<String> names = api.rvlAppealTypes().stream().map(RvlAppealTypes::normalize).collect(Collectors.toSet());
        return appealTypes.findActiveByNormalizedNameIn(names);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
