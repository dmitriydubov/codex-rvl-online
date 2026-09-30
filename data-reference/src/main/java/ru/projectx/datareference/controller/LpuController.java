package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.service.LpuCatalogService;

/** Каталог ЛПУ, карточка ЛПУ и услуги его прайсового договора (R-01…R-03). */
@InternalApi
public class LpuController {

    private final LpuCatalogService lpuCatalog;

    public LpuController(LpuCatalogService lpuCatalog) {
        this.lpuCatalog = lpuCatalog;
    }

    /** R-01: каталог ЛПУ, доступных застрахованным договора страхования. */
    @GetMapping("/lpu")
    public LpuCatalogResponse lpuCatalog(@RequestParam("contract_external_id") UUID contractExternalId) {
        return lpuCatalog.catalog(contractExternalId);
    }

    /** R-02: карточка ЛПУ с прайсовым договором. */
    @GetMapping("/lpu/{id}")
    public LpuCardResponse lpu(@PathVariable UUID id) {
        return lpuCatalog.card(id);
    }

    /** R-03: услуги прайсового договора по видам обращения РВЛ, с ценами по запросу. */
    @GetMapping("/lpu/{id}/services")
    public LpuServicesResponse lpuServices(
            @PathVariable UUID id, @RequestParam(name = "need_prices", defaultValue = "false") boolean needPrices) {

        return lpuCatalog.services(id, needPrices);
    }
}
