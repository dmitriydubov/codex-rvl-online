package ru.projectx.datareference.service;

import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.service.price.PriceBreakdown;
import ru.projectx.datareference.service.price.PriceSegment;
import ru.projectx.datareference.service.price.StayPeriod;

final class ServicePriceResponses {

    private ServicePriceResponses() {
    }

    static ServicePriceResponse of(NamedRef service, StayPeriod period, PriceBreakdown breakdown) {
        return new ServicePriceResponse(
                service,
                period.checkIn(),
                period.checkOut(),
                breakdown.segments().stream().map(ServicePriceResponses::segment).toList(),
                total(breakdown));
    }

    private static ServicePriceResponse.Segment segment(PriceSegment segment) {
        return new ServicePriceResponse.Segment(segment.from(), segment.to(), segment.nights(),
                segment.line().price(), segment.line().priceWithDiscount(),
                segment.amount(), segment.amountWithDiscount().orElse(null));
    }

    private static ServicePriceResponse.Total total(PriceBreakdown breakdown) {
        ServicePriceResponse.Basis basis = breakdown.discountedThroughout()
                ? ServicePriceResponse.Basis.DISCOUNT
                : ServicePriceResponse.Basis.BASE;
        return new ServicePriceResponse.Total(breakdown.nights(), breakdown.amount(),
                breakdown.amountWithDiscount().orElse(null), basis);
    }
}
