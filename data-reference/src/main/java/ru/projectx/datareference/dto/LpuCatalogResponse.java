package ru.projectx.datareference.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.Lpu;

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

    public record Skipped(UUID lpuId, String reason) {
    }
}
