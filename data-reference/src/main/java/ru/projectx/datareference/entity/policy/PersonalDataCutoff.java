package ru.projectx.datareference.entity.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record PersonalDataCutoff(LocalDate endedBefore, Instant archivedBy) {

    public static PersonalDataCutoff at(Instant now, Duration grace, ZoneId zone) {
        Instant limit = now.minus(grace);
        return new PersonalDataCutoff(LocalDate.ofInstant(limit, zone), limit);
    }
}
