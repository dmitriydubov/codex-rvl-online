package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceBreakdownTest {

    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);
    private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
    private static final LocalDate MAY_5 = LocalDate.of(2026, 5, 5);

    @Test
    void segment_multipliesBothPricesByNights_keepingKopecks() {
        PriceSegment segment = new PriceSegment(APRIL_10, APRIL_10.plusDays(3),
                PriceLines.line(APRIL_10, null, "4500.50", "4000.25"));

        assertThat(segment.nights()).isEqualTo(3);
        assertThat(segment.amount()).isEqualTo(new BigDecimal("13501.50"));
        assertThat(segment.amountWithDiscount()).contains(new BigDecimal("12000.75"));
    }

    @Test
    void segmentWithoutDiscountPrice_hasNoDiscountAmount() {
        PriceSegment segment = new PriceSegment(MAY_1, MAY_5, PriceLines.line(MAY_1, null, "12600.00", null));

        assertThat(segment.amountWithDiscount()).isEmpty();
    }

    @Test
    void exampleFromSpecification_totalsBaseAmount_andHasNoDiscountTotal() {
        PriceBreakdown breakdown = new PriceBreakdown(List.of(
                new PriceSegment(APRIL_10, MAY_1, PriceLines.line(APRIL_10, MAY_1.minusDays(1), "12000.00", "11400.00")),
                new PriceSegment(MAY_1, MAY_5, PriceLines.line(MAY_1, null, "12600.00", null))));

        assertThat(breakdown.nights()).isEqualTo(25);
        assertThat(breakdown.amount()).isEqualTo(new BigDecimal("302400.00"));
        assertThat(breakdown.amountWithDiscount()).isEmpty();
        assertThat(breakdown.discountedThroughout()).isFalse();
    }

    @Test
    void discountOnEverySegment_totalsDiscountAmount() {
        PriceBreakdown breakdown = new PriceBreakdown(List.of(
                new PriceSegment(APRIL_10, MAY_1, PriceLines.line(APRIL_10, MAY_1.minusDays(1), "5000.00", "4500.50")),
                new PriceSegment(MAY_1, MAY_5, PriceLines.line(MAY_1, null, "5200.40", "4700.25"))));

        assertThat(breakdown.amountWithDiscount()).contains(new BigDecimal("113311.50"));
        assertThat(breakdown.discountedThroughout()).isTrue();
    }
}
