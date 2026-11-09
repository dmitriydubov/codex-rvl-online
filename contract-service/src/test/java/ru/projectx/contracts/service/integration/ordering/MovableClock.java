package ru.projectx.contracts.service.integration.ordering;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class MovableClock extends Clock {

    private volatile Instant now;

    MovableClock(Instant start) {
        this.now = start;
    }

    void move(Duration step) {
        now = now.plus(step);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
