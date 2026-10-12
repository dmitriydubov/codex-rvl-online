package ru.projectx.datareference.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record Mkb10Code(
        UUID id,
        UUID externalId,
        String code,
        String name,
        @Nullable Integer hierarchyLevel,
        @Nullable String parentCode,
        boolean isArchive) {
}
