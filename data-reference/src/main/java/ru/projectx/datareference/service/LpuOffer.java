package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;

/**
 * Что ЛПУ предлагает по R-03 на {@code today}: учреждение, его прайсовый договор (базовая
 * логика B) и услуги этого договора по видам обращения РВЛ.
 *
 * @param appealTypes виды обращения РВЛ по GUID 1С — по ним услуги и отобраны
 */
record LpuOffer(
        Lpu lpu,
        PriceContract priceContract,
        Map<UUID, AppealType> appealTypes,
        List<ContragentService> services,
        LocalDate today) {

    /** Услуги отобраны по этим видам обращения, поэтому вид есть у каждой. */
    AppealType appealTypeOf(ContragentService service) {
        return appealTypes.get(service.externalAppealTypeId());
    }

    LpuServicesResponse response(List<LpuServicesResponse.Service> rows) {
        return LpuServicesResponse.of(lpu, priceContract.contract(), priceContract.kind(), rows);
    }
}
