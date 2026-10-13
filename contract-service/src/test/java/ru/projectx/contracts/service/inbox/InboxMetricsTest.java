package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class InboxMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InboxMetrics metrics = new InboxMetrics(registry);

    @Test
    void countFailed_incrementsCounterTaggedWithEventType() {
        metrics.countFailed("ContractTaskUpserted");
        metrics.countFailed("ContractTaskUpserted");
        metrics.countFailed("ContractTaskCancelled");

        assertThat(registry.get("contracts.inbox.failed").tag("event_type", "ContractTaskUpserted").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("contracts.inbox.failed").tag("event_type", "ContractTaskCancelled").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("contracts.inbox.failed").counter().getId().getDescription())
                .isEqualTo("входящие сообщения сервиса Заявок, отклонённые окончательно");
    }

    @Test
    void countLost_incrementsCounterTaggedWithReason() {
        metrics.countLost("unusable_envelope");
        metrics.countLost("unusable_envelope");
        metrics.countLost("unstorable_text");

        assertThat(registry.get("contracts.inbox.lost").tag("reason", "unusable_envelope").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("contracts.inbox.lost").tag("reason", "unstorable_text").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("contracts.inbox.lost").counter().getId().getDescription())
                .isEqualTo("входящие сообщения сервиса Заявок, подтверждённые и потерянные без записи в журнал");
    }
}
