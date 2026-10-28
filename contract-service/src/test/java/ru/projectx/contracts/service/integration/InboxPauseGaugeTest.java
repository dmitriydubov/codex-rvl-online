package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InboxPauseGaugeTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InboxConsumerPause pause = mock();

    @Test
    void gauge_isOneWhilePausedAndZeroOtherwise() {
        new InboxPauseGauge(pause).bindTo(registry);
        given(pause.paused()).willReturn(Optional.empty());

        assertThat(gauge().value()).isZero();

        given(pause.paused()).willReturn(Optional.of(new InboxConsumerPause.Paused(
                new StorageOutage("08006", "DataAccessResourceFailureException"), Instant.EPOCH, List.of())));
        assertThat(gauge().value()).isEqualTo(1);
        assertThat(gauge().getId().getDescription()).contains("паузе");
    }

    private Gauge gauge() {
        return registry.get("contracts.inbox.paused").gauge();
    }
}
