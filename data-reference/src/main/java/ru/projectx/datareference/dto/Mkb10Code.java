package ru.projectx.datareference.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Код МКБ-10 в выдаче R-07: код, наименование, уровень и код родителя; {@code is_archive}
 * нужен батчу — старые заявки ссылаются на архивные коды, и показать их надо.
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
