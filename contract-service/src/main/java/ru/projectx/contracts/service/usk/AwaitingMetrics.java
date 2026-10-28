package ru.projectx.contracts.service.usk;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class AwaitingMetrics {

    static final String SECONDS = "seconds";

    private final MeterRegistry registry;
    private final AwaitingGauges gauges;
    private final AtomicLong oldestSeconds = new AtomicLong();
    private final AtomicLong overdue = new AtomicLong();
    private final List<Meter> published = new ArrayList<>();
    private final Object publication = new Object();

    protected AwaitingMetrics(MeterRegistry registry, AwaitingGauges gauges) {
        this.registry = registry;
        this.gauges = gauges;
    }

    public void show(Duration oldest, long overdueCount) {
        oldestSeconds.set(oldest.toSeconds());
        overdue.set(overdueCount);
        publish();
    }

    public void withdraw() {
        synchronized (publication) {
            published.forEach(registry::remove);
            published.clear();
        }
    }

    private void publish() {
        synchronized (publication) {
            if (!published.isEmpty()) {
                return;
            }
            published.add(Gauge.builder(gauges.oldest(), oldestSeconds, AtomicLong::get)
                    .description(gauges.oldestDescription())
                    .baseUnit(SECONDS)
                    .register(registry));
            published.add(Gauge.builder(gauges.overdue(), overdue, AtomicLong::get)
                    .description(gauges.overdueDescription())
                    .register(registry));
        }
    }
}
