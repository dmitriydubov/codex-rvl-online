package ru.projectx.contracts.service.integration.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ThreadPausesTest {

    private final ThreadPauses pauses = new ThreadPauses();

    @Test
    void hold_waitsTheWholePauseAndReportsCompletion() {
        long started = System.nanoTime();

        boolean completed = pauses.hold(Duration.ofMillis(50));

        assertThat(completed).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(50));
    }

    @Test
    void hold_whenInterrupted_returnsAtOnceAndKeepsTheInterruptFlag() {
        Thread.currentThread().interrupt();

        boolean completed = pauses.hold(Duration.ofSeconds(30));

        assertThat(completed).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }
}
