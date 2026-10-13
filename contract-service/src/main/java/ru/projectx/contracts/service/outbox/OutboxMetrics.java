package ru.projectx.contracts.service.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    static final String FAILED = "contracts.outbox.failed";
    static final String EVENT_TYPE = "event_type";

    private final MeterRegistry registry;

    public OutboxMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countFailed(String eventType) {
        Counter.builder(FAILED)
                .description("исходящие сообщения, публикация которых отклонена окончательно")
                .tag(EVENT_TYPE, eventType)
                .register(registry)
                .increment();
    }
}
