package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LmPolicyAwaitingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LmPolicyAwaitingMetrics metrics = new LmPolicyAwaitingMetrics(registry);

    @Test
    void beforeTheFirstPass_bothGaugesAreZero() {
        assertThat(oldest().value()).isZero();
        assertThat(overdue().value()).isZero();
    }

    @Test
    void show_exposesTheOldestWaitInWholeSecondsAndTheOverdueCount() {
        metrics.show(Duration.ofMinutes(90).plusMillis(999), 3);

        assertThat(oldest().value()).isEqualTo(5400);
        assertThat(overdue().value()).isEqualTo(3);
    }

    @Test
    void laterPass_replacesTheEarlierSnapshot() {
        metrics.show(Duration.ofMinutes(90), 3);

        metrics.show(Duration.ZERO, 0);

        assertThat(oldest().value()).isZero();
        assertThat(overdue().value()).isZero();
    }

    @Test
    void gauges_carryUnitAndDescription() {
        assertThat(oldest().getId().getBaseUnit()).isEqualTo("seconds");
        assertThat(oldest().getId().getDescription()).isEqualTo("сколько ждёт ответа 1С:УСК самый старый переданный полис ЛМ");
        assertThat(overdue().getId().getBaseUnit()).isNull();
        assertThat(overdue().getId().getDescription()).isEqualTo("полисы ЛМ, которые ждут ответа 1С:УСК дольше порога");
    }

    @Test
    void withdraw_removesBothGaugesUntilTheNextSnapshotPublishesThemAgain() {
        metrics.show(Duration.ofMinutes(50), 1);

        metrics.withdraw();

        assertThat(registry.find("contracts.lm.policies.awaiting.oldest").gauge()).isNull();
        assertThat(registry.find("contracts.lm.policies.awaiting.overdue").gauge()).isNull();
        metrics.show(Duration.ofMinutes(50), 1);
        assertThat(oldest().value()).isEqualTo(3000);
        assertThat(overdue().value()).isEqualTo(1);
    }

    @Test
    void repeatedWithdraw_changesNothing() {
        metrics.withdraw();
        metrics.withdraw();

        assertThat(registry.getMeters()).isEmpty();
    }

    private Gauge oldest() {
        return registry.get("contracts.lm.policies.awaiting.oldest").gauge();
    }

    private Gauge overdue() {
        return registry.get("contracts.lm.policies.awaiting.overdue").gauge();
    }
}
