package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Lpu;

/**
 * Каталог ЛПУ, доступных застрахованным по договору. Учреждения с неоднозначным прайсовым договором уходят
 * в {@code skipped}: одна ошибочная запись в 1С не должна ронять весь каталог, но и молчать о ней нельзя.
 * Пустой {@code items} не ошибка: у договора может не быть действующих полисов с программами РВЛ.
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
