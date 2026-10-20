package ru.projectx.datareference.dto;

import java.util.UUID;
import ru.projectx.datareference.entity.catalog.OneCEntity;

public record NamedRef(UUID id, UUID externalId, String name) {

    static NamedRef of(OneCEntity entity, String name) {
        return new NamedRef(entity.id(), entity.externalId(), name);
    }
}
