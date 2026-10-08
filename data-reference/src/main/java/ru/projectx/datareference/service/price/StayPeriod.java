package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Период проживания: оплачиваются ночи, поэтому день выезда не оплачивается. Порядок дат проверяют до создания. */
public record StayPeriod(LocalDate checkIn, LocalDate checkOut) {

    /** Тип {@code long}, потому что период ещё не проверен на предел длины и может не поместиться в {@code int}. */
    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
