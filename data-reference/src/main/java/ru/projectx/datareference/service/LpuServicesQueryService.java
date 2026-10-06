package ru.projectx.datareference.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.repository.PriceListItemRepository;

/**
 * Услуги ЛПУ по прайсовому договору (R-03): позиции размещения по видам обращения РВЛ,
 * без цен или с ценами. Что ЛПУ предлагает, собирает {@link LpuOffers}; здесь — цены.
 *
 * <p>С ценами услуга без актуальных цен остаётся в списке с пустым перечнем: такая услуга
 * потом даст {@code 422} в расчёте стоимости, и видеть её надо заранее. Цены всех услуг — одним
 * запросом, а не по услуге: в прейскуранте санатория десятки позиций.
 *
 * <p>Услуги с ценами и без цен — два разных кэша, ключ — сегодняшний день контура и учреждение.
 */
@Service
public class LpuServicesQueryService implements LpuServiceReads {

    private final LpuOffers offers;
    private final PriceListItemRepository priceListItems;

    public LpuServicesQueryService(LpuOffers offers, PriceListItemRepository priceListItems) {
        this.offers = offers;
        this.priceListItems = priceListItems;
    }

    @Override
    @Cacheable(cacheNames = ReferenceCaches.LPU_SERVICES, keyGenerator = ReferenceCaches.TODAY_KEY)
    @RestReadTransaction
    public LpuServicesResponse withoutPrices(UUID lpuId) {
        LpuOffer offer = offers.of(lpuId);
        return offer.response(offer.services().stream()
                .map(service -> LpuServicesResponse.Service.withoutPrices(service, offer.appealTypeOf(service)))
                .toList());
    }

    @Override
    @Cacheable(cacheNames = ReferenceCaches.LPU_SERVICES_WITH_PRICES, keyGenerator = ReferenceCaches.TODAY_KEY)
    @RestReadTransaction
    public LpuServicesResponse withPrices(UUID lpuId) {
        LpuOffer offer = offers.of(lpuId);
        Map<UUID, List<PriceListItem>> prices = currentPrices(offer.services(), offer.today());
        return offer.response(offer.services().stream()
                .map(service -> LpuServicesResponse.Service.withPrices(service, offer.appealTypeOf(service),
                        prices.getOrDefault(service.id(), List.of())))
                .toList());
    }

    private Map<UUID, List<PriceListItem>> currentPrices(List<ContragentService> services, LocalDate today) {
        if (services.isEmpty()) {
            return Map.of();
        }
        List<UUID> serviceIds = services.stream().map(ContragentService::id).toList();
        return priceListItems.findCurrentByServiceIdIn(serviceIds, today).stream()
                .collect(Collectors.groupingBy(item -> item.service().id()));
    }
}
