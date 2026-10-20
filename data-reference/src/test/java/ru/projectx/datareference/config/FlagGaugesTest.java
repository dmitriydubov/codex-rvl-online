package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FlagGaugesTest {

    @Test
    void gauge_isOneWhileFlagIsSet_andZeroOtherwise_withDescription() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AtomicBoolean flag = new AtomicBoolean();

        FlagGauges.register(meters, "test.flag", "флаг для проверки", flag::get);
        Gauge gauge = meters.get("test.flag").gauge();

        assertThat(gauge.value()).isZero();
        flag.set(true);
        assertThat(gauge.value()).isEqualTo(1.0);
        flag.set(false);
        assertThat(gauge.value()).isZero();
        assertThat(gauge.getId().getDescription()).isEqualTo("флаг для проверки");
    }

    @Test
    void gauge_keepsItsFlag_afterGarbageCollection() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AtomicBoolean flag = new AtomicBoolean(true);
        FlagGauges.register(meters, "test.flag", "флаг для проверки", flag::get);
        WeakReference<Object> canary = new WeakReference<>(new Object());

        await().atMost(Duration.ofSeconds(10)).until(() -> {
            System.gc();
            return canary.get() == null;
        });

        assertThat(meters.get("test.flag").gauge().value()).isEqualTo(1.0);
    }
}
