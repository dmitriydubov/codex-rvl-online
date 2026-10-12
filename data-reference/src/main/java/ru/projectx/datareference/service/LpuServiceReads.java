package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuServicesResponse;

public interface LpuServiceReads {

    LpuServicesResponse withoutPrices(UUID lpuId);

    LpuServicesResponse withPrices(UUID lpuId);
}
