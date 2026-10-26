package ru.projectx.contracts.service.usk;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class AwaitingMetrics {

    static final String SECONDS = "seconds";

    private final AtomicLong oldestSeconds = new AtomicLong();
    private final AtomicLong overdue = new AtomicLong();

    protected AwaitingMetrics(MeterRegistry registry, AwaitingGauges gauges) {
        Gauge.builder(gauges.oldest(), oldestSeconds, AtomicLong::get)
                .description(gauges.oldestDescription())
                .baseUnit(SECONDS)
                .register(registry);
        Gauge.builder(gauges.overdue(), overdue, AtomicLong::get)
                .description(gauges.overdueDescription())
                .register(registry);
    }

    public void show(Duration oldest, long overdueCount) {
        oldestSeconds.set(oldest.toSeconds());
        overdue.set(overdueCount);
    }
}
