package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuServicesResponse;

/**
 * Чтение услуг прайсового договора учреждения без цен или с ценами. Два метода вместо флага, потому что
 * у ответов разный состав и «цены не запрашивали» должно отличаться от «цен нет».
 */
public interface LpuServiceReads {

    LpuServicesResponse withoutPrices(UUID lpuId);

    LpuServicesResponse withPrices(UUID lpuId);
}
