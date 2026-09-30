package ru.projectx.datareference.repository;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Строка выдачи по МКБ-10 — проекция запроса, а не сущность: код родителя
 * подтягивается тем же запросом через {@code external_parent_id}, иначе на каждую
 * строку выдачи пришёлся бы отдельный запрос за родителем.
 */
public record Mkb10Row(
        UUID id,
        UUID externalId,
        String code,
        String name,
        @Nullable Integer hierarchyLevel,
        @Nullable String parentCode,
        boolean isArchive) {
}
