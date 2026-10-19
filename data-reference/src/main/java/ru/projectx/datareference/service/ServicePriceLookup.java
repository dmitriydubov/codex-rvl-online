package ru.projectx.datareference.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.MissingReference;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.service.price.PriceLine;
import ru.projectx.datareference.service.price.StayPeriod;

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
    public PricedService source(UUID serviceId, StayPeriod period) {
        ContragentService service = contragentServices.findById(serviceId)
                .orElseThrow(() -> MissingReference.SERVICE.notFound(serviceId));
        requireOffered(service, offers.of(service.contract().lpu().id()));
        List<PriceLine> lines = priceListItems
                .findWithinPeriodByServiceId(serviceId, period.checkIn(), period.checkOut()).stream()
                .map(PriceLine::of)
                .toList();
        return new PricedService(new NamedRef(service.id(), service.externalId(), service.name()), lines);
    }

    private static void requireOffered(ContragentService service, LpuOffer offer) {
        requireNotArchived(service);
        if (!service.contract().id().equals(offer.priceContract().contract().id())) {
            throw IncompleteReferenceException.serviceOutsidePriceContract(offer.lpu().id());
        }
        if (offer.services().stream().noneMatch(offered -> offered.id().equals(service.id()))) {
            throw MissingReference.SERVICE.notFound(service.id());
        }
    }

    private static void requireNotArchived(ContragentService service) {
        if (service.isArchive()) {
            throw MissingReference.SERVICE.notFound(service.id());
        }
    }
}
