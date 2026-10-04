package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * Компонент здоровья приёма из 1С:УСК: DOWN с причиной, пока потребители стоят, UP — когда работают. Причина —
 * код SQLSTATE и тип отказа, без текста ошибки.
 */
@ExtendWith(MockitoExtension.class)
class OneCIngestHealthIndicatorTest {

    private static final Instant SINCE = Instant.parse("2026-10-04T12:00:00Z");

    @Mock
    private OneCConsumerPause pause;

    @Test
    void consumersRunning_isUpWithoutDetails() {
        when(pause.paused()).thenReturn(Optional.empty());

        Health health = new OneCIngestHealthIndicator(pause).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
    }

    @Test
    void consumersPaused_isDownWithSqlStateFailureSinceAndQueues() {
        when(pause.paused()).thenReturn(Optional.of(new OneCConsumerPause.Paused(
                new StorageOutage("08001", "CannotCreateTransactionException"), SINCE, Set.of("q-policies"))));

        Health health = new OneCIngestHealthIndicator(pause).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("sqlstate", "08001")
                .containsEntry("failure", "CannotCreateTransactionException")
                .containsEntry("since", "2026-10-04T12:00:00Z")
                .containsEntry("queues", Set.of("q-policies"));
    }
}
