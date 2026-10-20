package ru.projectx.datareference.service.rest;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ReferenceApiProperties;

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
