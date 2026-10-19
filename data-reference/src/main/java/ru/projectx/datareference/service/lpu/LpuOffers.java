package ru.projectx.datareference.service.lpu;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.catalog.AppealType;
import ru.projectx.datareference.entity.catalog.ContragentService;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.repository.catalog.ContragentServiceRepository;
import ru.projectx.datareference.service.rest.ReferenceCalendar;

@Component
public class LpuOffers {

    private final ActiveLpus activeLpus;
    private final PriceContractResolver priceContracts;
    private final RvlAppealTypes rvlAppealTypes;
    private final ContragentServiceRepository contragentServices;
    private final ReferenceCalendar calendar;

    public LpuOffers(
            ActiveLpus activeLpus,
            PriceContractResolver priceContracts,
            RvlAppealTypes rvlAppealTypes,
            ContragentServiceRepository contragentServices,
            ReferenceCalendar calendar) {

        this.activeLpus = activeLpus;
        this.priceContracts = priceContracts;
        this.rvlAppealTypes = rvlAppealTypes;
        this.contragentServices = contragentServices;
        this.calendar = calendar;
    }

    public LpuOffer of(UUID lpuId) {
        Lpu lpu = activeLpus.byId(lpuId);
        LocalDate today = calendar.today();
        PriceContract priceContract = priceContracts.resolve(lpuId, today);
        Map<UUID, AppealType> appealTypes = rvlAppealTypes.byExternalId();
        return new LpuOffer(lpu, priceContract, appealTypes, rvlServices(priceContract, appealTypes), today);
    }

    private List<ContragentService> rvlServices(PriceContract priceContract, Map<UUID, AppealType> appealTypes) {
        if (appealTypes.isEmpty()) {
            return List.of();
        }
        return contragentServices.findActiveByContractAndAppealTypeIn(priceContract.contract().id(), appealTypes.keySet());
    }
}
