package ru.projectx.datareference.repository.catalog;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record Mkb10Row(
        UUID id,
        UUID externalId,
        String code,
        String name,
        @Nullable Integer hierarchyLevel,
        @Nullable String parentCode,
        boolean isArchive) {
}
