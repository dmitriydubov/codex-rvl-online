package ru.projectx.contracts.service.usk.request;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class UskPreparationMetrics {

    static final String FAILED = "contracts.usk.preparation.failed";
    static final String EVENT_TYPE = "event_type";

    private final MeterRegistry registry;

    public UskPreparationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countFailed(String eventType) {
        Counter.builder(FAILED)
                .description("записи о выпуске, передача которых в 1С:УСК не подготовлена из-за внутренней ошибки")
                .tag(EVENT_TYPE, eventType)
                .register(registry)
                .increment();
    }
}
