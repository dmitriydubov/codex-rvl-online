package ru.projectx.datareference.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.LpuContract;

public record LpuCardResponse(
        UUID id,
        UUID externalId,
        String name,
        @Nullable String region,
        @Nullable String inn,
        @Nullable String kpp,
        @Nullable String address,
        PriceContractRef priceContract) {

    public static LpuCardResponse of(Lpu lpu, LpuContract priceContract, PriceContractRef.Kind kind) {
        return new LpuCardResponse(lpu.id(), lpu.externalId(), lpu.name(), lpu.region(), lpu.inn(), lpu.kpp(),
                lpu.address(), PriceContractRef.of(priceContract, kind));
    }
}
