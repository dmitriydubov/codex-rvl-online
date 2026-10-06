package ru.projectx.datareference.acceptance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Часы сервиса на стенде: идут как системные, пока сценарий их не остановил, а остановленные двигаются только
 * шагами сценария. По ним сервис считает «сегодня» ответов и срок жизни ответов в кэше.
 *
 * <p>Часы в другой зоне ({@link #withZone}) — вид тех же часов, а не их снимок: сервис берёт московскую зону для
 * дня контура, и остановка и сдвиг должны быть видны и там.
 */
final class StandClock extends Clock {

    private final AtomicReference<Optional<Instant>> stopped;
    private final ZoneId zone;

    StandClock() {
        this(new AtomicReference<>(Optional.empty()), ZoneOffset.UTC);
    }

    private StandClock(AtomicReference<Optional<Instant>> stopped, ZoneId zone) {
        this.stopped = stopped;
        this.zone = zone;
    }

    void stopAt(Instant moment) {
        stopped.set(Optional.of(moment));
    }

    /** Сдвигает остановленные часы; идущие часы сдвигать нельзя — шаг сценария ошибся бы молча. */
    void advance(Duration duration) {
        Instant moment = stopped.get().orElseThrow(() -> new IllegalStateException("часы стенда не остановлены"));
        stopped.set(Optional.of(moment.plus(duration)));
    }

    void resume() {
        stopped.set(Optional.empty());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new StandClock(stopped, otherZone);
    }

    @Override
    public Instant instant() {
        return stopped.get().orElseGet(Instant::now);
    }
}
