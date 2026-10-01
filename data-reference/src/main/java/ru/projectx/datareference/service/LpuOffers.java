package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.repository.ContragentServiceRepository;

/**
 * Собирает {@link LpuOffer}: действующее ЛПУ, его прайсовый договор на сегодняшний день контура
 * и услуги договора по видам обращения РВЛ. Нет ни одного вида обращения РВЛ в справочнике —
 * услуг нет, и запрос за ними не идёт.
 */
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

    LpuOffer of(UUID lpuId) {
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
