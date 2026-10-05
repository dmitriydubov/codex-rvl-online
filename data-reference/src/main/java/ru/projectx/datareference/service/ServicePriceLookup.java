package ru.projectx.datareference.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.service.price.PriceLine;
import ru.projectx.datareference.service.price.ServicePriceCalculator;
import ru.projectx.datareference.service.price.StayPeriod;

/**
 * Читает из базы всё, что нужно для расчёта стоимости услуги за период, и считает её в одной транзакции чтения.
 *
 * <p>Считается только услуга, которую учреждение отдаёт в перечне своих услуг. Отбор тот же, что у перечня, и
 * берётся из {@link LpuOffers}, чтобы перечень и расчёт не разошлись: учреждение не в архиве, прайсовый договор
 * выбран однозначно, услуга относится к нему и идёт по виду обращения РВЛ. Поэтому учреждение проверяется раньше
 * услуги. Услуга другого договора того же учреждения — отказ 422: считать по ценам договора, который больше не
 * прайсовый, нельзя. Услуга прайсового договора, которой нет в перечне (в архиве или не РВЛ), для вызывающего не
 * существует — 404.
 *
 * <p>Строки прайса читаются одним запросом на весь период, а не по суткам.
 */
@Component
public class ServicePriceLookup {

    private final ContragentServiceRepository contragentServices;
    private final LpuOffers offers;
    private final PriceListItemRepository priceListItems;

    public ServicePriceLookup(
            ContragentServiceRepository contragentServices, LpuOffers offers, PriceListItemRepository priceListItems) {

        this.contragentServices = contragentServices;
        this.offers = offers;
        this.priceListItems = priceListItems;
    }

    @RestReadTransaction
    public ServicePriceResponse price(UUID serviceId, StayPeriod period) {
        ContragentService service = contragentServices.findById(serviceId)
                .orElseThrow(() -> ReferenceNotFoundException.service(serviceId));
        requireOffered(service, offers.of(service.contract().lpu().id()));
        List<PriceLine> lines = priceListItems
                .findWithinPeriodByServiceId(serviceId, period.checkIn(), period.checkOut()).stream()
                .map(PriceLine::of)
                .toList();
        return ServicePriceResponses.of(service, period, ServicePriceCalculator.calculate(serviceId, period, lines));
    }

    private static void requireOffered(ContragentService service, LpuOffer offer) {
        if (!service.contract().id().equals(offer.priceContract().contract().id())) {
            throw IncompleteReferenceException.serviceOutsidePriceContract(offer.lpu().id());
        }
        if (offer.services().stream().noneMatch(offered -> offered.id().equals(service.id()))) {
            throw ReferenceNotFoundException.service(service.id());
        }
    }
}
