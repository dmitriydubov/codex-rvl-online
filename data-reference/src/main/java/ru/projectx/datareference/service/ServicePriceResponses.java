package ru.projectx.datareference.service;

import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.service.price.PriceBreakdown;
import ru.projectx.datareference.service.price.PriceSegment;
import ru.projectx.datareference.service.price.StayPeriod;

/**
 * Собирает ответ о стоимости услуги из посчитанной разбивки. Основной суммой для сооплаты отмечается сумма со
 * скидкой, когда она есть у итога, иначе сумма без скидки.
 */
final class ServicePriceResponses {

    private ServicePriceResponses() {
    }

    static ServicePriceResponse of(ContragentService service, StayPeriod period, PriceBreakdown breakdown) {
        return new ServicePriceResponse(
                new NamedRef(service.id(), service.externalId(), service.name()),
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
