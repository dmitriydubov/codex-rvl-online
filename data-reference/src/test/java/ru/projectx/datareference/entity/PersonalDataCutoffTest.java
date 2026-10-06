package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Граница хранения персональных данных считается от момента «сейчас минус запас»: полис, переведённый в архив не
 * позже этого момента, и полис, срок которого кончился раньше календарного дня этого момента в зоне сервиса, свои
 * персональные данные больше не хранят. День берётся по зоне сервиса, а не по UTC: полис действует по свой день
 * окончания включительно, и ошибка зоны сдвинула бы границу на сутки.
 */
class PersonalDataCutoffTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final Duration GRACE = Duration.ofDays(30);

    @Test
    void cutoff_isNowMinusGrace_forArchive() {
        Instant now = Instant.parse("2026-03-31T09:00:00Z");

        PersonalDataCutoff cutoff = PersonalDataCutoff.at(now, GRACE, MOSCOW);

        assertThat(cutoff.archivedBy()).isEqualTo(Instant.parse("2026-03-01T09:00:00Z"));
    }

    @Test
    void cutoffDay_isTheCalendarDayOfNowMinusGrace_inServiceZone() {
        Instant justAfterMoscowMidnight = Instant.parse("2026-03-02T21:30:00Z");

        PersonalDataCutoff cutoff = PersonalDataCutoff.at(justAfterMoscowMidnight, GRACE, MOSCOW);

        assertThat(cutoff.endedBefore()).isEqualTo(LocalDate.of(2026, 2, 1));
    }
}
