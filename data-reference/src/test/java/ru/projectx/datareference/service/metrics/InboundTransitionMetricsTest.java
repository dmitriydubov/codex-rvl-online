package ru.projectx.datareference.service.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.OneCStream;

class InboundTransitionMetricsTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InboundTransitionMetrics metrics = new InboundTransitionMetrics(registry);

    @AfterEach
    void closeTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void transitionOutsideTransaction_isCountedAtOnce() {
        InboundMessage message = message(OneCStream.MKB10);
        message.markProcessed(NOW);

        metrics.record(message);

        assertThat(transitions("MKB10", "PROCESSED").count()).isEqualTo(1.0);
    }

    @Test
    void transitionInTransaction_isCountedOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        InboundMessage message = message(OneCStream.POLICIES);
        message.markFailed("лестница повторов исчерпана");

        metrics.record(message);
        assertThat(transitions("POLICIES", "FAILED").count()).isZero();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(transitions("POLICIES", "FAILED").count()).isEqualTo(1.0);
    }

    @Test
    void transitionInRolledBackTransaction_isNotCounted() {
        TransactionSynchronizationManager.initSynchronization();
        InboundMessage message = message(OneCStream.POLICIES);
        message.markSkipped("не ДМС", NOW);

        metrics.record(message);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(transitions("POLICIES", "SKIPPED").count()).isZero();
    }

    @Test
    void eachStreamAndStatus_isItsOwnSeries() {
        InboundMessage retried = message(OneCStream.MKB10);
        retried.markRetrying("timeout", NOW);
        InboundMessage buffered = message(OneCStream.PRICE_LISTS);
        buffered.markBuffered(UUID.randomUUID(), null, 3);

        metrics.record(retried);
        metrics.record(buffered);

        assertThat(transitions("MKB10", "RETRY").count()).isEqualTo(1.0);
        assertThat(transitions("PRICE_LISTS", "BUFFERED").count()).isEqualTo(1.0);
        assertThat(registry.find(InboundTransitionMetrics.TRANSITIONS).counters())
                .filteredOn(counter -> counter.count() > 0).hasSize(2);
    }

    @Test
    void creation_registersZeroSeriesForEveryStreamAndTargetStatus() {
        assertThat(registry.find(InboundTransitionMetrics.TRANSITIONS).counters())
                .hasSize(OneCStream.values().length * 6)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.find(InboundTransitionMetrics.TRANSITIONS).counters())
                .extracting(counter -> counter.getId().getTag("status"))
                .containsOnly("PROCESSED", "SKIPPED", "BUFFERED", "AWAITING_PARENT", "RETRY", "FAILED");
    }

    @Test
    void newStatus_isNotCounted() {
        metrics.record(message(OneCStream.MKB10));

        assertThat(registry.find(InboundTransitionMetrics.TRANSITIONS).tag("status", "NEW").counter()).isNull();
        assertThat(registry.find(InboundTransitionMetrics.TRANSITIONS).counters())
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    void counter_isDescribed() {
        assertThat(transitions("MKB10", "FAILED").getId().getDescription()).startsWith("Переходы сырых сообщений 1С");
    }

    private Counter transitions(String stream, String status) {
        return registry.get(InboundTransitionMetrics.TRANSITIONS).tag("stream", stream).tag("status", status).counter();
    }

    private static InboundMessage message(OneCStream stream) {
        return InboundMessage.receive(new InboundReceipt(UUID.randomUUID(), stream, "queue", NOW), "{}");
    }
}
