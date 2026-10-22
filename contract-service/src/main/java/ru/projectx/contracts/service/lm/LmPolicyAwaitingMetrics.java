package ru.projectx.contracts.service.lm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class LmPolicyAwaitingMetrics {

    static final String OLDEST = "contracts.lm.policies.awaiting.oldest";
    static final String OVERDUE = "contracts.lm.policies.awaiting.overdue";
    static final String SECONDS = "seconds";

    private final AtomicLong oldestSeconds = new AtomicLong();
    private final AtomicLong overdue = new AtomicLong();

    public LmPolicyAwaitingMetrics(MeterRegistry registry) {
        Gauge.builder(OLDEST, oldestSeconds, AtomicLong::get)
                .description("сколько ждёт ответа 1С:УСК самый старый переданный полис ЛМ")
                .baseUnit(SECONDS)
                .register(registry);
        Gauge.builder(OVERDUE, overdue, AtomicLong::get)
                .description("полисы ЛМ, которые ждут ответа 1С:УСК дольше порога")
                .register(registry);
    }

    public void show(Duration oldest, long overdueCount) {
        oldestSeconds.set(oldest.toSeconds());
        overdue.set(overdueCount);
    }
}
