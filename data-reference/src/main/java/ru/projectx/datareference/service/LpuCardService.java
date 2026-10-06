package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.entity.Lpu;

/**
 * Карточка ЛПУ (R-02): учреждение и его прайсовый договор по базовой логике B. Ответ по одному
 * ЛПУ, подменить его нечем — неоднозначный договор даёт {@code 409}, отсутствующий — {@code 422}.
 * Карточка кэшируется по сегодняшнему дню контура и учреждению, отказ в кэш не попадает.
 */
@Service
public class LpuCardService implements LpuCardReads {

    private final ActiveLpus activeLpus;
    private final PriceContractResolver priceContracts;
    private final ReferenceCalendar calendar;

    public LpuCardService(ActiveLpus activeLpus, PriceContractResolver priceContracts, ReferenceCalendar calendar) {
        this.activeLpus = activeLpus;
        this.priceContracts = priceContracts;
        this.calendar = calendar;
    }

    @Override
    @Cacheable(cacheNames = ReferenceCaches.LPU_CARD, keyGenerator = ReferenceCaches.TODAY_KEY)
    @RestReadTransaction
    public LpuCardResponse card(UUID lpuId) {
        Lpu lpu = activeLpus.byId(lpuId);
        PriceContract priceContract = priceContracts.resolve(lpuId, calendar.today());
        return LpuCardResponse.of(lpu, priceContract.contract(), priceContract.kind());
    }
}
