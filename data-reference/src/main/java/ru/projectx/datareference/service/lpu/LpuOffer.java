package ru.projectx.datareference.service.lpu;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;

public record LpuOffer(
        Lpu lpu,
        PriceContract priceContract,
        Map<UUID, AppealType> appealTypes,
        List<ContragentService> services,
        LocalDate today) {

    AppealType appealTypeOf(ContragentService service) {
        return appealTypes.get(service.externalAppealTypeId());
    }

    LpuServicesResponse response(List<LpuServicesResponse.Service> rows) {
        return LpuServicesResponse.of(lpu, priceContract.contract(), priceContract.kind(), rows);
    }
}
