package ru.projectx.datareference.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ReferenceApiProperties;

/**
 * Сегодняшний день в зоне контура для правила «действует на сегодня»: сервис живёт в UTC, и три часа в сутки договоры
 * иначе отставали бы на день. День уходит в запрос параметром, а не {@code CURRENT_DATE}, чтобы быть одинаковым во
 * всём ответе и управляться часами в тестах.
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
