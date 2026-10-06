package ru.projectx.datareference.config;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Время для сроков жизни кэша — по часам сервиса, а не по своему счётчику Caffeine. Так срок жизни кэша идёт
 * по тому же времени, что и «сегодня» в ответах, и тесты управляют обоими одними остановленными часами.
 *
 * <p>Часы сервиса — системные часы UTC. Если их переведут назад, ответы в кэше проживут дольше на величину
 * перевода, но не дольше, чем до ближайшего сброса по сообщению 1С.
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
