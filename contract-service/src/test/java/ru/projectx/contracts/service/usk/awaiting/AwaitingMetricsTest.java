package ru.projectx.contracts.service.usk.awaiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AwaitingMetricsTest {

    private static final String OLDEST = "contracts.test.awaiting.oldest";
    private static final String OVERDUE = "contracts.test.awaiting.overdue";

    private final MeterRegistry registry = spy(new SimpleMeterRegistry());
    private final AwaitingMetrics metrics = new AwaitingMetrics(registry,
            new AwaitingGauges(OLDEST, "самый старый", OVERDUE, "просроченные"));

    @Test
    void repeatedSnapshots_publishEachGaugeOnceSoThatWithdrawRemovesItOnce() {
        metrics.show(Duration.ofMinutes(1), 1);
        metrics.show(Duration.ofMinutes(2), 2);

        assertThat(registry.find(OLDEST).gauges()).hasSize(1);
        assertThat(registry.find(OVERDUE).gauges()).hasSize(1);

        metrics.withdraw();

        verify(registry, times(2)).remove(any(Meter.class));
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void snapshotAfterWithdraw_publishesEachGaugeOnceAgain() {
        metrics.show(Duration.ofMinutes(1), 1);
        metrics.withdraw();

        metrics.show(Duration.ofMinutes(3), 3);
        metrics.show(Duration.ofMinutes(4), 4);
        metrics.withdraw();

        verify(registry, times(4)).remove(any(Meter.class));
        assertThat(registry.getMeters()).isEmpty();
    }
}
