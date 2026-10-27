package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KmContractAwaitingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final KmContractAwaitingMetrics metrics = new KmContractAwaitingMetrics(registry);

    @Test
    void beforeTheFirstPass_bothGaugesAreZero() {
        assertThat(oldest().value()).isZero();
        assertThat(overdue().value()).isZero();
    }

    @Test
    void show_exposesTheOldestWaitInWholeSecondsAndTheOverdueCount() {
        metrics.show(Duration.ofMinutes(150).plusMillis(999), 3);

        assertThat(oldest().value()).isEqualTo(9000);
        assertThat(overdue().value()).isEqualTo(3);
    }

    @Test
    void gauges_carryUnitAndDescriptionOfKmContracts() {
        assertThat(oldest().getId().getBaseUnit()).isEqualTo("seconds");
        assertThat(oldest().getId().getDescription())
                .isEqualTo("сколько ждёт ответа 1С:УСК самый старый переданный договор КМ");
        assertThat(overdue().getId().getBaseUnit()).isNull();
        assertThat(overdue().getId().getDescription()).isEqualTo("договоры КМ, которые ждут ответа 1С:УСК дольше порога");
    }

    @Test
    void withdraw_removesBothGaugesUntilTheNextSnapshotPublishesThemAgain() {
        metrics.show(Duration.ofMinutes(50), 1);

        metrics.withdraw();

        assertThat(registry.find("contracts.km.contracts.awaiting.oldest").gauge()).isNull();
        assertThat(registry.find("contracts.km.contracts.awaiting.overdue").gauge()).isNull();
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
        return registry.get("contracts.km.contracts.awaiting.oldest").gauge();
    }

    private Gauge overdue() {
        return registry.get("contracts.km.contracts.awaiting.overdue").gauge();
    }
}
