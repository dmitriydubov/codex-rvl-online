package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.exception.AmbiguousPriceException;
import ru.projectx.datareference.exception.PriceNotCoveredException;

class ServicePriceCalculatorTest {

    private static final UUID SERVICE_ID = UUID.fromString("5e000000-0000-4000-8000-000000000001");
    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);
    private static final LocalDate APRIL_30 = LocalDate.of(2026, 4, 30);
    private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
    private static final LocalDate MAY_5 = LocalDate.of(2026, 5, 5);

    @Test
    void twoAdjacentLines_giveTwoSegments_splitOnFirstDayOfSecondLine() {
        PriceLine april = PriceLines.line(LocalDate.of(2026, 1, 1), APRIL_30, "12000.00", "11400.00");
        PriceLine may = PriceLines.line(MAY_1, null, "12600.00", null);

        PriceBreakdown breakdown = calculate(APRIL_10, MAY_5, List.of(april, may));

        assertThat(breakdown.segments()).containsExactly(
                new PriceSegment(APRIL_10, MAY_1, april),
                new PriceSegment(MAY_1, MAY_5, may));
    }

    @Test
    void adjacentLinesWithSamePrice_stayTwoSegments() {
        PriceLine first = PriceLines.line(APRIL_10, APRIL_10, "9000.00", null);
        PriceLine second = PriceLines.line(APRIL_10.plusDays(1), null, "9000.00", null);

        PriceBreakdown breakdown = calculate(APRIL_10, APRIL_10.plusDays(2), List.of(first, second));

        assertThat(breakdown.segments()).extracting(PriceSegment::line).containsExactly(first, second);
    }

    @Test
    void lineEndingBeforeCheckOutDay_coversWholePeriod() {
        PriceLine line = PriceLines.line(APRIL_10, MAY_5.minusDays(1), "4000.00", null);

        PriceBreakdown breakdown = calculate(APRIL_10, MAY_5, List.of(line));

        assertThat(breakdown.segments()).containsExactly(new PriceSegment(APRIL_10, MAY_5, line));
    }

    @Test
    void uncoveredNights_areRefused_withEveryUncoveredInterval() {
        List<PriceLine> lines = List.of(
                PriceLines.line(LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 14), "3000.00", null),
                PriceLines.line(LocalDate.of(2026, 4, 17), LocalDate.of(2026, 4, 18), "3000.00", null));

        assertThatThrownBy(() -> calculate(APRIL_10, LocalDate.of(2026, 4, 21), lines))
                .isInstanceOfSatisfying(PriceNotCoveredException.class, notCovered -> {
                    assertThat(notCovered.serviceId()).isEqualTo(SERVICE_ID);
                    assertThat(notCovered.intervals()).containsExactly(
                            new PriceNotCoveredException.Interval(APRIL_10, LocalDate.of(2026, 4, 12)),
                            new PriceNotCoveredException.Interval(LocalDate.of(2026, 4, 15), LocalDate.of(2026, 4, 17)),
                            new PriceNotCoveredException.Interval(LocalDate.of(2026, 4, 19), LocalDate.of(2026, 4, 21)));
                });
    }

    @Test
    void noLines_refusesWholePeriodAsOneInterval() {
        assertThatThrownBy(() -> calculate(APRIL_10, APRIL_10.plusDays(3), List.of()))
                .isInstanceOfSatisfying(PriceNotCoveredException.class, notCovered -> assertThat(notCovered.intervals())
                        .containsExactly(new PriceNotCoveredException.Interval(APRIL_10, APRIL_10.plusDays(3))));
    }

    @Test
    void twoLinesOnOneNight_areRefused_listingOverlappingLinesByStart() {
        PriceLine later = PriceLines.line(LocalDate.of(2026, 4, 15), null, "11000.00", null);
        PriceLine earlier = PriceLines.line(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20), "10000.00", null);
        PriceLine elsewhere = PriceLines.line(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "9000.00", null);

        assertThatThrownBy(() -> calculate(APRIL_10, LocalDate.of(2026, 4, 16), List.of(later, elsewhere, earlier)))
                .isInstanceOfSatisfying(AmbiguousPriceException.class, ambiguous -> {
                    assertThat(ambiguous.serviceId()).isEqualTo(SERVICE_ID);
                    assertThat(ambiguous.rows()).containsExactly(
                            new AmbiguousPriceException.PriceRow(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20)),
                            new AmbiguousPriceException.PriceRow(LocalDate.of(2026, 4, 15), null));
                });
    }

    @Test
    void overlappingLinesStartingSameDay_listClosedOneBeforeOpenOne() {
        PriceLine open = PriceLines.line(APRIL_10, null, "11000.00", null);
        PriceLine closed = PriceLines.line(APRIL_10, APRIL_30, "10000.00", null);

        assertThatThrownBy(() -> calculate(APRIL_10, APRIL_10.plusDays(1), List.of(open, closed)))
                .isInstanceOfSatisfying(AmbiguousPriceException.class, ambiguous -> assertThat(ambiguous.rows())
                        .containsExactly(
                                new AmbiguousPriceException.PriceRow(APRIL_10, APRIL_30),
                                new AmbiguousPriceException.PriceRow(APRIL_10, null)));
    }

    @Test
    void overlapAfterCheckOut_doesNotMatter() {
        PriceLine earlier = PriceLines.line(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20), "10000.00", null);
        PriceLine later = PriceLines.line(LocalDate.of(2026, 4, 15), null, "11000.00", null);

        PriceBreakdown breakdown = calculate(APRIL_10, LocalDate.of(2026, 4, 15), List.of(earlier, later));

        assertThat(breakdown.amount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void overlapAndGapInOnePeriod_isAmbiguityNotGap() {
        PriceLine earlier = PriceLines.line(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20), "10000.00", null);
        PriceLine later = PriceLines.line(LocalDate.of(2026, 4, 15), null, "11000.00", null);

        assertThatThrownBy(() -> calculate(LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 16), List.of(earlier, later)))
                .isInstanceOf(AmbiguousPriceException.class);
    }

    @Test
    void lineReachingBeyondBothEndsOfPeriod_isCutToPeriod() {
        PriceLine line = PriceLines.line(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "4000.00", null);

        PriceBreakdown breakdown = calculate(APRIL_10, MAY_5, List.of(line));

        assertThat(breakdown.segments()).containsExactly(new PriceSegment(APRIL_10, MAY_5, line));
    }

    @Test
    void linesOutsidePeriod_areIgnored() {
        PriceLine inside = PriceLines.line(APRIL_10, null, "4000.00", null);
        PriceLine endedDayBeforeCheckIn = PriceLines.line(LocalDate.of(2026, 3, 1), APRIL_10.minusDays(1), "1.00", null);
        PriceLine startsOnCheckOutDay = PriceLines.line(MAY_5, null, "2.00", null);

        PriceBreakdown breakdown = calculate(APRIL_10, MAY_5, List.of(endedDayBeforeCheckIn, inside, startsOnCheckOutDay));

        assertThat(breakdown.segments()).containsExactly(new PriceSegment(APRIL_10, MAY_5, inside));
    }

    @Test
    void periodOfThousandsOfYears_isPricedByLineBoundaries() {
        LocalDate checkOut = LocalDate.of(9999, 12, 31);
        PriceLine line = PriceLines.line(LocalDate.of(2000, 1, 1), null, "1.00", null);

        PriceBreakdown breakdown = calculate(LocalDate.of(2000, 1, 1), checkOut, List.of(line));

        assertThat(breakdown.nights()).isEqualTo(2_921_939);
        assertThat(breakdown.amount()).isEqualByComparingTo("2921939.00");
    }

    private static PriceBreakdown calculate(LocalDate checkIn, LocalDate checkOut, List<PriceLine> lines) {
        return ServicePriceCalculator.calculate(SERVICE_ID, new StayPeriod(checkIn, checkOut), lines);
    }
}
