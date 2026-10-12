package ru.projectx.datareference.config;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

public final class ClockTicker implements Ticker {

    private final Clock clock;

    public ClockTicker(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long read() {
        return TimeUnit.MILLISECONDS.toNanos(clock.millis());
    }
}
