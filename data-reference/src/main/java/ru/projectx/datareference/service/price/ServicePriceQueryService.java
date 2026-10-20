package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.config.cache.ReferenceCaches;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.exception.InvalidRequestException;

@Service
public class ServicePriceQueryService implements ServicePriceReads {

    private static final String CHECK_OUT = "date_to";
    private static final String CHECK_OUT_AFTER_CHECK_IN = "позже date_from";
    private static final String STAY_LIMIT_PREFIX = "не больше ";
    private static final String STAY_LIMIT_SUFFIX = " ночей после date_from";

    private final ServicePriceLookup lookup;
    private final ReferenceApiProperties api;

    public ServicePriceQueryService(ServicePriceLookup lookup, ReferenceApiProperties api) {
        this.lookup = lookup;
        this.api = api;
    }

    @Override
    @Cacheable(cacheNames = ReferenceCaches.SERVICE_PRICES, keyGenerator = ReferenceCaches.TODAY_KEY)
    public ServicePriceResponse price(UUID serviceId, LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidRequestException(CHECK_OUT, CHECK_OUT_AFTER_CHECK_IN);
        }
        StayPeriod period = new StayPeriod(checkIn, checkOut);
        requireNotLongerThanAllowed(period);
        PricedService priced = lookup.source(serviceId, period);
        return ServicePriceResponses.of(priced.service(), period,
                ServicePriceCalculator.calculate(serviceId, period, priced.lines()));
    }

    private void requireNotLongerThanAllowed(StayPeriod period) {
        int maxNights = api.price().maxNights();
        if (period.nights() > maxNights) {
            throw new InvalidRequestException(CHECK_OUT, STAY_LIMIT_PREFIX + maxNights + STAY_LIMIT_SUFFIX);
        }
    }
}
