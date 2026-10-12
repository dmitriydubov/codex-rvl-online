package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockTickerTest {

    private static final Instant NOON = Instant.parse("2026-10-06T09:00:00Z");

    @Test
    void readsServiceClockInNanoseconds() {
        ClockTicker ticker = new ClockTicker(Clock.fixed(NOON, ZoneOffset.UTC));

        assertThat(ticker.read()).isEqualTo(NOON.toEpochMilli() * 1_000_000L);
    }

    @Test
    void movesTogetherWithServiceClock() {
        Clock later = Clock.offset(Clock.fixed(NOON, ZoneOffset.UTC), Duration.ofMinutes(10));

        long elapsed = new ClockTicker(later).read() - new ClockTicker(Clock.fixed(NOON, ZoneOffset.UTC)).read();

        assertThat(elapsed).isEqualTo(Duration.ofMinutes(10).toNanos());
    }
}
