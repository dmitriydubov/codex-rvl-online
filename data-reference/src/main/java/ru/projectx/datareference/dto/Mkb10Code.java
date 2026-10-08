package ru.projectx.datareference.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Код МКБ-10 в выдаче. Признак архива нужен при раскрытии перечня кодов: старые заявки ссылаются
 * на архивные коды, и показать их всё равно надо.
 */
public record Mkb10Code(
        UUID id,
        UUID externalId,
        String code,
        String name,
        @Nullable Integer hierarchyLevel,
        @Nullable String parentCode,
        boolean isArchive) {
}
