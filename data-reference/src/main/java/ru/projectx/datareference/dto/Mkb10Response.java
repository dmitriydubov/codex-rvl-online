package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Коды МКБ-10 для R-07: подсказка по строке или раскрытие перечня кодов. В обоих
 * режимах — код, наименование, уровень и код родителя; {@code is_archive} нужен
 * батчу: старые заявки ссылаются на архивные коды, и показать их надо.
 */
public record Mkb10Response(List<Item> items) {

    public record Item(
            UUID id,
            UUID externalId,
            String code,
            String name,
            @Nullable Integer hierarchyLevel,
            @Nullable String parentCode,
            boolean isArchive) {
    }
}
