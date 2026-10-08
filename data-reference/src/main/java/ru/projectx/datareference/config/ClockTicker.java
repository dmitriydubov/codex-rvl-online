package ru.projectx.datareference.config;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Время для сроков жизни кэша по часам сервиса, а не по счётчику Caffeine: так срок кэша и «сегодня» в ответах идут
 * по одним часам, и тесты управляют обоими. Если часы переведут назад, ответы проживут в кэше дольше на величину
 * перевода, но не дольше ближайшего сброса по сообщению 1С.
 */
public final class ClockTicker implements Ticker {

    private final Clock clock;

    public ClockTicker(Clock clock) {
        this.clock = clock;
    }

    /** Наносекунды от начала эпохи с точностью часов сервиса, до миллисекунды. */
    @Override
    public long read() {
        return TimeUnit.MILLISECONDS.toNanos(clock.millis());
    }
}
