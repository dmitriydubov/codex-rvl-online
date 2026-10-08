package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;

/** Что учреждение предлагает на {@code today}: прайсовый договор и его услуги по видам обращения РВЛ. */
record LpuOffer(
        Lpu lpu,
        PriceContract priceContract,
        Map<UUID, AppealType> appealTypes,
        List<ContragentService> services,
        LocalDate today) {

    /** Услуги отобраны по этим видам обращения, поэтому вид найдётся у каждой. */
    AppealType appealTypeOf(ContragentService service) {
        return appealTypes.get(service.externalAppealTypeId());
    }

    LpuServicesResponse response(List<LpuServicesResponse.Service> rows) {
        return LpuServicesResponse.of(lpu, priceContract.contract(), priceContract.kind(), rows);
    }
}
