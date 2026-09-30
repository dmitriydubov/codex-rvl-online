package ru.projectx.datareference.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ReferenceApiProperties;

/**
 * Календарный день контура для правила «действует на сегодня» из ТЗ на API.
 *
 * <p>Сервис живёт в UTC, а день считается в зоне контура (Europe/Moscow): иначе три часа
 * в сутки договоры отставали бы на день. День вычисляется один раз на запрос и уходит
 * в запрос параметром, а не {@code CURRENT_DATE}: так он одинаков во всех условиях
 * одного ответа, а тесты управляют им остановленными часами.
 */
@Component
public class ReferenceCalendar {

    private final Clock clock;
    private final ReferenceApiProperties api;

    public ReferenceCalendar(Clock clock, ReferenceApiProperties api) {
        this.clock = clock;
        this.api = api;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(api.zone()));
    }
}
