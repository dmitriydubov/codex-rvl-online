package ru.projectx.contracts.service.inbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InboxMetrics {

    static final String FAILED = "contracts.inbox.failed";
    static final String EVENT_TYPE = "event_type";

    private final MeterRegistry registry;

    public InboxMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countFailed(String eventType) {
        Counter.builder(FAILED)
                .description("входящие сообщения сервиса Заявок, отклонённые окончательно")
                .tag(EVENT_TYPE, eventType)
                .register(registry)
                .increment();
    }
}
