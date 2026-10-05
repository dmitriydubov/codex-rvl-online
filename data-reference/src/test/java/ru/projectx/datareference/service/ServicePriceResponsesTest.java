package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.dto.ServicePriceResponse;
import ru.projectx.datareference.service.price.PriceBreakdown;
import ru.projectx.datareference.service.price.PriceLine;
import ru.projectx.datareference.service.price.PriceSegment;
import ru.projectx.datareference.service.price.StayPeriod;

/**
 * Основной суммой для сооплаты ответ называет сумму со скидкой, только когда цена со скидкой есть у каждого
 * отрезка; иначе основная — сумма без скидки, а итога со скидкой нет.
 */
class ServicePriceResponsesTest {

    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);
    private static final LocalDate APRIL_12 = LocalDate.of(2026, 4, 12);
    private static final LocalDate APRIL_13 = LocalDate.of(2026, 4, 13);

    private final NamedRef service = new NamedRef(UUID.randomUUID(), UUID.randomUUID(), "Размещение");

    @Test
    void discountOnEverySegment_makesDiscountedTotalTheBasis() {
        PriceBreakdown breakdown = new PriceBreakdown(List.of(
                new PriceSegment(APRIL_10, APRIL_12, line("3500.00", "3000.00")),
                new PriceSegment(APRIL_12, APRIL_13, line("4000.00", "3600.00"))));

        ServicePriceResponse response = ServicePriceResponses.of(service, new StayPeriod(APRIL_10, APRIL_13), breakdown);

        assertThat(response.service()).isSameAs(service);
        assertThat(response.dateFrom()).isEqualTo(APRIL_10);
        assertThat(response.dateTo()).isEqualTo(APRIL_13);
        assertThat(response.total()).isEqualTo(new ServicePriceResponse.Total(3, new BigDecimal("11000.00"),
                new BigDecimal("9600.00"), ServicePriceResponse.Basis.DISCOUNT));
        assertThat(response.segments()).containsExactly(
                new ServicePriceResponse.Segment(APRIL_10, APRIL_12, 2, new BigDecimal("3500.00"),
                        new BigDecimal("3000.00"), new BigDecimal("7000.00"), new BigDecimal("6000.00")),
                new ServicePriceResponse.Segment(APRIL_12, APRIL_13, 1, new BigDecimal("4000.00"),
                        new BigDecimal("3600.00"), new BigDecimal("4000.00"), new BigDecimal("3600.00")));
    }

    @Test
    void segmentWithoutDiscount_makesBaseTotalTheBasis_andLeavesNoDiscountedTotal() {
        PriceBreakdown breakdown = new PriceBreakdown(List.of(
                new PriceSegment(APRIL_10, APRIL_12, line("3500.00", "3000.00")),
                new PriceSegment(APRIL_12, APRIL_13, line("4000.00", null))));

        ServicePriceResponse response = ServicePriceResponses.of(service, new StayPeriod(APRIL_10, APRIL_13), breakdown);

        assertThat(response.total()).isEqualTo(new ServicePriceResponse.Total(3, new BigDecimal("11000.00"),
                null, ServicePriceResponse.Basis.BASE));
        assertThat(response.segments().get(1).amountWithDiscount()).isNull();
    }

    private static PriceLine line(String price, @Nullable String discounted) {
        return new PriceLine(UUID.randomUUID(), APRIL_10, null, new BigDecimal(price),
                discounted == null ? null : new BigDecimal(discounted));
    }
}
