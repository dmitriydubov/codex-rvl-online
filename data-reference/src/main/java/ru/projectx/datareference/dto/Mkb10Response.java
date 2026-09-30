package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.repository.Mkb10Row;

/**
 * Коды МКБ-10 для R-07: подсказка по строке или раскрытие перечня кодов. В обоих
 * режимах — код, наименование, уровень и код родителя; {@code is_archive} нужен
 * батчу: старые заявки ссылаются на архивные коды, и показать их надо.
 */
public record Mkb10Response(List<Item> items) {

    public static Mkb10Response of(List<Mkb10Row> rows) {
        return new Mkb10Response(rows.stream().map(Item::of).toList());
    }

    public record Item(
            UUID id,
            UUID externalId,
            String code,
            String name,
            @Nullable Integer hierarchyLevel,
            @Nullable String parentCode,
            boolean isArchive) {

        static Item of(Mkb10Row row) {
            return new Item(row.id(), row.externalId(), row.code(), row.name(), row.hierarchyLevel(),
                    row.parentCode(), row.isArchive());
        }
    }
}
