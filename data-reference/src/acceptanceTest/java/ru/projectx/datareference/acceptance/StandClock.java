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

/**
 * Часы сервиса на стенде: идут как системные, пока сценарий их не остановил, а остановленные двигаются только
 * шагами сценария. По ним сервис считает «сегодня» ответов и срок жизни ответов в кэше, а шаги — моменты, которые
 * кладут в базу.
 *
 * <p>Идущие часы можно перенести на другой день ({@link #runningOn}): «сегодня» по Москве — заданная дата, время
 * суток — настоящее. Так приёмка проверяется на любую дату без правки кода.
 *
 * <p>Часы в другой зоне ({@link #withZone}) — вид тех же часов, а не их снимок: сервис берёт московскую зону для
 * дня контура, и остановка и сдвиг должны быть видны и там.
 */
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

    /**
     * Идущие часы, у которых «сегодня» по Москве — {@code today}, а время суток — настоящее. Сдвиг считается по
     * календарю Москвы, а не числом суток по 24 часа: смещение зоны менялось, и для прошлых дат оно другое.
     */
    static StandClock runningOn(LocalDate today) {
        ZonedDateTime now = ZonedDateTime.now(MOSCOW);
        return new StandClock(Duration.between(now, now.with(today)));
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

    /**
     * Текущий момент этих часов для параметра запроса: драйвер PostgreSQL принимает отметку времени с зоной как
     * {@link OffsetDateTime}, а {@link Instant} — нет. Шаги кладут в базу время сервиса, а не {@code now()} базы:
     * иначе на перенесённых часах сроки, отсчитанные сервисом, разошлись бы с моментами в таблицах.
     */
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
