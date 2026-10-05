package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.util.List;

/**
 * Период проживания, за который считается стоимость услуги: с дня заезда до дня выезда.
 *
 * <p>Оплачиваются ночи, поэтому сутки периода идут от дня заезда включительно до дня выезда, а день выезда не
 * оплачивается. Что выезд позже заезда, проверяется до создания периода, на входе запроса.
 */
public record StayPeriod(LocalDate checkIn, LocalDate checkOut) {

    /** Оплачиваемые сутки по порядку: каждая дата — ночь, которая начинается в этот день. */
    public List<LocalDate> nights() {
        return checkIn.datesUntil(checkOut).toList();
    }
}
