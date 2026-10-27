package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.integration.leader.Context;
import ru.projectx.contracts.support.TestLeadership;

class LeaderRoleGaugeTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BackgroundLeadership leadership = TestLeadership.standby();

    @Test
    void gauge_followsTheRoleOfTheInstance() {
        new LeaderRoleGauge(leadership).bindTo(registry);

        assertThat(gauge().value()).isZero();
        leadership.onGranted(mock(Context.class));
        assertThat(gauge().value()).isEqualTo(1);
        leadership.onRevoked(mock(Context.class));
        assertThat(gauge().value()).isZero();
    }

    @Test
    void gauge_carriesADescription() {
        new LeaderRoleGauge(leadership).bindTo(registry);

        assertThat(gauge().getId().getDescription()).contains("ведущий");
    }

    private Gauge gauge() {
        return registry.get("contracts.leader").gauge();
    }
}
