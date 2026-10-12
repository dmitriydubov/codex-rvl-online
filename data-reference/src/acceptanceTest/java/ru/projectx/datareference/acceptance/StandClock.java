package ru.projectx.datareference.acceptance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class StandClock extends Clock {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final AtomicReference<Optional<Instant>> stopped;
    private final Duration shift;
    private final ZoneId zone;

    StandClock() {
        this(Duration.ZERO);
    }

    private StandClock(Duration shift) {
        this(new AtomicReference<>(Optional.empty()), shift, ZoneOffset.UTC);
    }

    private StandClock(AtomicReference<Optional<Instant>> stopped, Duration shift, ZoneId zone) {
        this.stopped = stopped;
        this.shift = shift;
        this.zone = zone;
    }

    static StandClock runningOn(LocalDate today) {
        ZonedDateTime now = ZonedDateTime.now(MOSCOW);
        StandClock clock = new StandClock(Duration.between(now, now.with(today)));
        LocalDate shown = LocalDate.now(clock.withZone(MOSCOW));
        if (!shown.equals(today)) {
            throw new IllegalStateException("часы стенда показывают " + shown + " вместо " + today);
        }
        return clock;
    }

    void stopAt(Instant moment) {
        stopped.set(Optional.of(moment));
    }

    void advance(Duration duration) {
        Instant moment = stopped.get().orElseThrow(() -> new IllegalStateException("часы стенда не остановлены"));
        stopped.set(Optional.of(moment.plus(duration)));
    }

    void resume() {
        stopped.set(Optional.empty());
    }

    OffsetDateTime moment() {
        return OffsetDateTime.ofInstant(instant(), ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new StandClock(stopped, shift, otherZone);
    }

    @Override
    public Instant instant() {
        return stopped.get().orElseGet(() -> Instant.now().plus(shift));
    }
}
