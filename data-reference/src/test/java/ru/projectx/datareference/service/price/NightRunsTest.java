package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Подряд идущие ночи с одним признаком склеиваются в отрезок, а конец отрезка — следующий день после его последней
 * ночи. Так одной записью называются и отрезок расчёта, и непокрытый интервал.
 */
class NightRunsTest {

    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);

    @Test
    void consecutiveNightsWithSameKey_formOneRun_endingDayAfterLastNight() {
        List<LocalDate> nights = APRIL_10.datesUntil(APRIL_10.plusDays(3)).toList();

        List<NightRun<String>> runs = NightRuns.of(nights, night -> "А");

        assertThat(runs).containsExactly(new NightRun<>("А", APRIL_10, APRIL_10.plusDays(3)));
    }

    @Test
    void keyChange_startsNewRun_andSameKeyLater_startsAnotherOne() {
        List<LocalDate> nights = APRIL_10.datesUntil(APRIL_10.plusDays(5)).toList();
        List<String> keys = List.of("А", "А", "Б", "А", "А");

        List<NightRun<String>> runs = NightRuns.of(nights, night -> keys.get(nights.indexOf(night)));

        assertThat(runs).containsExactly(
                new NightRun<>("А", APRIL_10, APRIL_10.plusDays(2)),
                new NightRun<>("Б", APRIL_10.plusDays(2), APRIL_10.plusDays(3)),
                new NightRun<>("А", APRIL_10.plusDays(3), APRIL_10.plusDays(5)));
    }

    @Test
    void noNights_noRuns() {
        assertThat(NightRuns.of(List.of(), night -> "А")).isEmpty();
    }
}
