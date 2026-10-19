package ru.projectx.datareference.entity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

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
