package ru.projectx.datareference.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;

/**
 * Карточка ЛПУ (R-02): то же, что в каталоге, плюс адрес и прайсовый договор с его
 * сроками. Полей {@code booking_limit_days} и {@code short_description} из ТЗ здесь
 * нет: у них нет источника в потоках 1С, а поле без источника в контракт не попадает (В-8).
 */
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
