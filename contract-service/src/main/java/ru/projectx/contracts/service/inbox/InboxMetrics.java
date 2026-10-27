package ru.projectx.contracts.service.inbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InboxMetrics {

    static final String FAILED = "contracts.inbox.failed";
    static final String LOST = "contracts.inbox.lost";
    static final String QUARANTINED = "contracts.inbox.quarantined";
    static final String EVENT_TYPE = "event_type";
    static final String REASON = "reason";

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

    public void countQuarantined() {
        Counter.builder(QUARANTINED)
                .description("входящие сообщения, тело которых база не принимает: записаны в карантин и подтверждены")
                .register(registry)
                .increment();
    }

    public void countLost(String reason) {
        Counter.builder(LOST)
                .description("входящие сообщения сервиса Заявок, подтверждённые и потерянные без записи в журнал")
                .tag(REASON, reason)
                .register(registry)
                .increment();
    }
}
