package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.integration.leader.Context;

class LeaderRoleGaugeTest {

    private static final LeaderLock LOCK =
            new LeaderLock("data-reference", "onec-background", "0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");

    @Test
    void gauge_followsTheRole() {
        BackgroundLeadership leadership = new BackgroundLeadership(LOCK);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        new LeaderRoleGauge(leadership).bindTo(meters);
        Gauge leader = meters.get("onec.leader").gauge();

        assertThat(leader.value()).isZero();
        leadership.onGranted(mock(Context.class));
        assertThat(leader.value()).isEqualTo(1.0);
        leadership.onRevoked(mock(Context.class));
        assertThat(leader.value()).isZero();
    }
}
