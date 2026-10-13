package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxMetrics metrics = new OutboxMetrics(registry);

    @Test
    void countFailed_incrementsCounterTaggedWithEventType() {
        metrics.countFailed("ContractStatusChanged");
        metrics.countFailed("ContractStatusChanged");
        metrics.countFailed("KmContractSubmitted");

        assertThat(registry.get("contracts.outbox.failed").tag("event_type", "ContractStatusChanged").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("contracts.outbox.failed").tag("event_type", "KmContractSubmitted").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("contracts.outbox.failed").counter().getId().getDescription())
                .isEqualTo("исходящие сообщения, публикация которых отклонена окончательно");
    }
}
