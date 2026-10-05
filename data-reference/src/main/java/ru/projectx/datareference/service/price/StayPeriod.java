package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Период проживания, за который считается стоимость услуги: с дня заезда до дня выезда.
 *
 * <p>Оплачиваются ночи, поэтому сутки периода идут от дня заезда включительно до дня выезда, а день выезда не
 * оплачивается. Что выезд позже заезда, проверяется до создания периода, на входе запроса.
 */
public record StayPeriod(LocalDate checkIn, LocalDate checkOut) {

    /**
     * Число оплачиваемых ночей: дней от заезда до выезда, день выезда не считается. Тип {@code long}, потому что
     * период на входе ещё не проверен на предел длины и может не поместиться в {@code int}.
     */
    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
