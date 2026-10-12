package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StayPeriodTest {

    @Test
    void nights_countsDaysFromCheckInToCheckOutExclusive() {
        StayPeriod period = new StayPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 4));

        assertThat(period.nights()).isEqualTo(3);
    }

    @Test
    void nights_acrossMonthAndLeapDay_countsCalendarDays() {
        StayPeriod period = new StayPeriod(LocalDate.of(2028, 2, 27), LocalDate.of(2028, 3, 2));

        assertThat(period.nights()).isEqualTo(4);
    }

    @Test
    void nights_ofVeryLongPeriod_doesNotOverflow() {
        StayPeriod period = new StayPeriod(LocalDate.of(-999_999_999, 1, 1), LocalDate.of(999_999_999, 1, 1));

        assertThat(period.nights()).isGreaterThan(Integer.MAX_VALUE);
    }
}
