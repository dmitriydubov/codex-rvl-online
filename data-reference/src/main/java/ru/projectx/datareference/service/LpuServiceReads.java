package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.LpuServicesResponse;

/**
 * Услуги прайсового договора ЛПУ по видам обращения РВЛ (R-03) — без цен или с ценами. Два
 * метода, а не флаг: у ответов разный состав, «цены не запрашивали» и «цен нет» различимы.
 */
public interface LpuServiceReads {

    LpuServicesResponse withoutPrices(UUID lpuId);

    LpuServicesResponse withPrices(UUID lpuId);
}
