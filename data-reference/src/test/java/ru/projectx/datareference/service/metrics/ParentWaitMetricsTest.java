package ru.projectx.datareference.service.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.inbound.OneCStream;

class ParentWaitMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ParentWaitMetrics metrics = new ParentWaitMetrics(registry);

    @Test
    void creation_registersAZeroSeriesForEveryStream() {
        assertThat(registry.find(ParentWaitMetrics.EXPIRED).counters())
                .hasSize(OneCStream.values().length)
                .allSatisfy(counter -> {
                    assertThat(counter.count()).isZero();
                    assertThat(counter.getId().getTags()).hasSize(1);
                });
    }

    @Test
    void expired_countsOneMessageOfItsStream() {
        metrics.expired(OneCStream.LPU_CONTACTS);
        metrics.expired(OneCStream.LPU_CONTACTS);

        assertThat(registry.get(ParentWaitMetrics.EXPIRED).tag("stream", "LPU_CONTACTS").counter().count())
                .isEqualTo(2);
        assertThat(registry.get(ParentWaitMetrics.EXPIRED).tag("stream", "LPU_CONTRACTS").counter().count())
                .isZero();
    }
}
