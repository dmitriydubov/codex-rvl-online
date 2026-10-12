package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ru.projectx.datareference.entity.OneCStream;

class OneCIngestMetricsTest {

    private static final String POLICIES_QUEUE = "1c-usk_to_rvl-online_policies";
    private static final String MKB10_QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OneCIngestMetrics metrics = IngestTestMetrics.on(registry);

    @Test
    void delivery_isCountedByStreamQueueAndOutcome() {
        metrics.record(delivery(OneCStream.POLICIES, POLICIES_QUEUE), IngestOutcome.STORED);
        metrics.record(delivery(OneCStream.POLICIES, POLICIES_QUEUE), IngestOutcome.STORED);
        metrics.record(delivery(OneCStream.MKB10, MKB10_QUEUE), IngestOutcome.DUPLICATE);

        assertThat(deliveries("POLICIES", POLICIES_QUEUE, "stored").count()).isEqualTo(2.0);
        assertThat(deliveries("MKB10", MKB10_QUEUE, "duplicate").count()).isEqualTo(1.0);
        assertThat(registry.find(OneCIngestMetrics.DELIVERIES).tag("outcome", "duplicate")
                .tag("stream", "POLICIES").tag("queue", POLICIES_QUEUE).counter()).isNull();
    }

    @Test
    void everyOutcome_hasItsOwnTagValue() {
        for (IngestOutcome outcome : IngestOutcome.values()) {
            metrics.record(delivery(OneCStream.MKB10, MKB10_QUEUE), outcome);
        }

        assertThat(registry.find(OneCIngestMetrics.DELIVERIES).tag("queue", MKB10_QUEUE).counters())
                .extracting(counter -> counter.getId().getTag("outcome"))
                .containsExactlyInAnyOrder(
                        "stored", "duplicate", "quarantined", "unidentified", "returned", "storage_outage");
    }

    @Test
    void deliveryWithoutQueue_isCountedUnderUnknownQueue() {
        metrics.record(delivery(OneCStream.MKB10, null), IngestOutcome.STORED);

        assertThat(deliveries("MKB10", "unknown", "stored").count()).isEqualTo(1.0);
    }

    @Test
    void deliveryCounter_isDescribed() {
        metrics.record(delivery(OneCStream.MKB10, MKB10_QUEUE), IngestOutcome.STORED);

        assertThat(deliveries("MKB10", MKB10_QUEUE, "stored").getId().getDescription())
                .startsWith("Доставки из очередей 1С:УСК");
    }

    @Test
    void creation_registersZeroSeriesForEveryQueueAndOutcome() {
        assertThat(registry.find(OneCIngestMetrics.DELIVERIES).counters())
                .hasSize(OneCQueue.values().length * IngestOutcome.values().length)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(registry.get(OneCIngestMetrics.DELIVERIES)
                .tag("stream", "PRICE_LISTS").tag("queue", "q-price-lists-final").tag("outcome", "unidentified")
                .counter().count()).isZero();
        assertThat(registry.get(OneCIngestMetrics.DELIVERIES)
                .tag("stream", "POLICIES").tag("queue", "q-policies").tag("outcome", "stored")
                .counter().count()).isZero();
        assertThat(registry.get(OneCIngestMetrics.PAUSES).counter().count()).isZero();
    }

    @Test
    void deliveryFromConfiguredQueue_countsIntoTheRegisteredSeries() {
        metrics.record(delivery(OneCStream.POLICIES, "q-policies"), IngestOutcome.QUARANTINED);

        assertThat(deliveries("POLICIES", "q-policies", "quarantined").count()).isEqualTo(1.0);
        assertThat(registry.find(OneCIngestMetrics.DELIVERIES).counters())
                .hasSize(OneCQueue.values().length * IngestOutcome.values().length);
    }

    @Test
    void pause_isCountedOnEachStop() {
        metrics.paused();
        metrics.paused();

        Counter pauses = registry.get(OneCIngestMetrics.PAUSES).counter();
        assertThat(pauses.count()).isEqualTo(2.0);
        assertThat(pauses.getId().getDescription()).startsWith("Остановки потребителей");
    }

    private Counter deliveries(String stream, String queue, String outcome) {
        return registry.get(OneCIngestMetrics.DELIVERIES)
                .tag("stream", stream).tag("queue", queue).tag("outcome", outcome).counter();
    }

    private static OneCDelivery delivery(OneCStream stream, @Nullable String queue) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(queue);
        return new OneCDelivery(stream, new Message(new byte[0], properties), mock(Channel.class));
    }
}
