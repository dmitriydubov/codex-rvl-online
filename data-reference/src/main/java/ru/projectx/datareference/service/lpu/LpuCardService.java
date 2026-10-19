package ru.projectx.datareference.service.lpu;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.service.rest.ReferenceCalendar;
import ru.projectx.datareference.service.rest.RestReadTransaction;

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
