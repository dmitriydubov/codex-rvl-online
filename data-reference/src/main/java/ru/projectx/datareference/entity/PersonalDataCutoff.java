package ru.projectx.datareference.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Граница хранения персональных данных полисов на один момент. Полис, срок действия которого кончился раньше дня
 * {@code endedBefore}, и полис, переведённый в архив не позже момента {@code archivedBy}, персональные данные больше
 * не хранят.
 *
 * <p>Полис действует по день окончания включительно, поэтому его срок кончается в начале следующих суток в зоне
 * сервиса. Запас отсчитывается от этого начала: «окончание раньше дня момента "сейчас минус запас"» ровно это и
 * означает.
 *
 * @param endedBefore день, раньше которого должен кончиться срок действия полиса
 * @param archivedBy  момент, не позже которого полис должен быть переведён в архив
 */
public record PersonalDataCutoff(LocalDate endedBefore, Instant archivedBy) {

    /** Граница на момент {@code now} при запасе {@code grace}; день окончания полиса считается в зоне {@code zone}. */
    public static PersonalDataCutoff at(Instant now, Duration grace, ZoneId zone) {
        Instant limit = now.minus(grace);
        return new PersonalDataCutoff(LocalDate.ofInstant(limit, zone), limit);
    }
}
