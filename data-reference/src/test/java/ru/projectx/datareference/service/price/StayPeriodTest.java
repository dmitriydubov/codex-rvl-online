package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Оплачиваются ночи: от дня заезда включительно до дня выезда, а сам день выезда в период не входит. */
class StayPeriodTest {

    @Test
    void nights_runFromCheckInDay_andStopBeforeCheckOutDay() {
        StayPeriod period = new StayPeriod(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 2));

        assertThat(period.nights()).containsExactly(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1));
    }
}
