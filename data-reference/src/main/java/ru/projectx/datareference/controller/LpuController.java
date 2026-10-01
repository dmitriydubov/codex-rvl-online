package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.service.LpuCardReads;
import ru.projectx.datareference.service.LpuCatalogReads;
import ru.projectx.datareference.service.LpuServiceReads;

/**
 * Каталог ЛПУ, карточка ЛПУ и услуги его прайсового договора (R-01…R-03). Параметр
 * {@code need_prices} выбирает ответ R-03 — с ценами или без, — других решений здесь нет.
 */
@InternalApi
public class LpuController {

    private final LpuCatalogReads catalogs;
    private final LpuCardReads cards;
    private final LpuServiceReads services;

    public LpuController(LpuCatalogReads catalogs, LpuCardReads cards, LpuServiceReads services) {
        this.catalogs = catalogs;
        this.cards = cards;
        this.services = services;
    }

    /** R-01: каталог ЛПУ, доступных застрахованным договора страхования. */
    @GetMapping("/lpu")
    public LpuCatalogResponse lpuCatalog(@RequestParam("contract_external_id") UUID contractExternalId) {
        return catalogs.catalog(contractExternalId);
    }

    /** R-02: карточка ЛПУ с прайсовым договором. */
    @GetMapping("/lpu/{id}")
    public LpuCardResponse lpu(@PathVariable UUID id) {
        return cards.card(id);
    }

    /** R-03: услуги прайсового договора по видам обращения РВЛ, с ценами по запросу. */
    @GetMapping("/lpu/{id}/services")
    public LpuServicesResponse lpuServices(
            @PathVariable UUID id, @RequestParam(name = "need_prices", defaultValue = "false") boolean needPrices) {

        return needPrices ? services.withPrices(id) : services.withoutPrices(id);
    }
}
