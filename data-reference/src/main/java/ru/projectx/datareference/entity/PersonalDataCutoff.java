package ru.projectx.datareference.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Граница хранения персональных данных полисов: их больше не хранят полис, кончившийся раньше дня {@code endedBefore},
 * и полис, ушедший в архив не позже {@code archivedBy}. Полис действует по день окончания включительно, поэтому
 * запас отсчитывается от начала следующих суток в зоне сервиса.
 */
public record PersonalDataCutoff(LocalDate endedBefore, Instant archivedBy) {

    /** Граница на момент {@code now} при запасе {@code grace}; день окончания полиса считается в зоне {@code zone}. */
    public static PersonalDataCutoff at(Instant now, Duration grace, ZoneId zone) {
        Instant limit = now.minus(grace);
        return new PersonalDataCutoff(LocalDate.ofInstant(limit, zone), limit);
    }
}
