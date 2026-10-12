package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record StayPeriod(LocalDate checkIn, LocalDate checkOut) {

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
