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
 * Виды обращения РВЛ: наименования из конфигурации, найденные в справочнике
 * {@code appeal_types}. По ним R-01 отбирает строки перечня программы, а R-03 — услуги.
 *
 * <p>Отбор по наименованию хрупок: переименование или лишний пробел в 1С молча обнулят
 * выдачу. Отсюда три меры из ответа В-9: сравнение без учёта регистра и краевых пробелов,
 * список в конфигурации, а на старте сервиса — WARN, если хотя бы одно наименование
 * не нашлось. Кеша нет: справочник маленький, запрос дешёвый, а устаревший кеш здесь
 * стоил бы дороже запроса.
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

    /** Действующие виды обращения РВЛ по GUID 1С — ключу, которым на них ссылаются перечни и услуги. */
    public Map<UUID, AppealType> byExternalId() {
        return found().stream().collect(Collectors.toMap(AppealType::externalId, Function.identity()));
    }

    public Set<UUID> externalIds() {
        return byExternalId().keySet();
    }

    /** Наименования из конфигурации, которых в справочнике нет: по ним R-01 и R-03 отдадут пустую выдачу. */
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
