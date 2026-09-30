package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Lpu;

/**
 * Каталог ЛПУ по договору страхования (R-01). {@code items} — действующие учреждения,
 * доступные застрахованным договора, по наименованию. {@code skipped} — учреждения,
 * исключённые из-за неоднозначного прайсового договора: одна кривая строка в 1С
 * не должна класть каталог целиком, но и молчать о ней нельзя (В-6). Пустой
 * {@code items} — не ошибка: у договора может не быть действующих полисов
 * с программами РВЛ.
 */
public record LpuCatalogResponse(List<Item> items, List<Skipped> skipped) {

    public record Item(
            UUID id,
            UUID externalId,
            String name,
            @Nullable String region,
            @Nullable String inn,
            @Nullable String kpp) {

        public static Item of(Lpu lpu) {
            return new Item(lpu.id(), lpu.externalId(), lpu.name(), lpu.region(), lpu.inn(), lpu.kpp());
        }
    }

    /** {@code reason} — код той ошибки, которой ответила бы точечная ручка. */
    public record Skipped(UUID lpuId, String reason) {
    }
}
