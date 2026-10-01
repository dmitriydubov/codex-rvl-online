package ru.projectx.datareference.dto;

import java.util.UUID;
import ru.projectx.datareference.entity.OneCEntity;

/**
 * Ссылка на запись справочника в ответе: наш {@code id}, GUID 1С и наименование. Так в ответах
 * названы ЛПУ, программа страхования и вид обращения — у каждого ключи одни и те же.
 */
public record NamedRef(UUID id, UUID externalId, String name) {

    static NamedRef of(OneCEntity entity, String name) {
        return new NamedRef(entity.id(), entity.externalId(), name);
    }
}
