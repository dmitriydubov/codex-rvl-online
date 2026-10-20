package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.config.OneCJson;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.RejectedMessage;
import ru.projectx.datareference.service.inbound.QuarantineService;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;
import ru.projectx.datareference.service.leadership.LeadershipLostException;
import ru.projectx.datareference.service.onec.OneCIngestService;

@ExtendWith(MockitoExtension.class)
class OneCQueueListenerTest {

    private static final long DELIVERY_TAG = 42L;
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000a");
    private static final Duration STORAGE_CHECK_INTERVAL = Duration.ofSeconds(10);
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String UNTRANSLATABLE_CHARACTER = "22P05";
    private static final String CONNECTION_FAILURE = "08006";

    @Mock
    private OneCIngestService ingestService;

    @Mock
    private QuarantineService quarantineService;

    @Mock
    private OneCConsumerPause consumers;

    @Mock
    private Channel channel;

    @Mock
    private BackgroundLeadership leadership;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private final OneCIngestMetrics metrics = IngestTestMetrics.on(meters);

    @Test
    void storedMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(true);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertThat(outcomes()).containsExactly("stored");
    }

    @Test
    void metricsFailureAfterStorage_isNotTakenForStorageFailure() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(true);
        OneCQueueListener listener = listener(Duration.ofMillis(1), IngestTestMetrics.on(new FailingMeterRegistry()));

        assertThatIllegalStateException()
                .isThrownBy(() -> listener.receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel));

        verifyNoInteractions(consumers, quarantineService, channel);
    }

    @Test
    void duplicateMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(false);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        assertThat(outcomes()).containsExactly("duplicate");
    }

    @Test
    void storedMessage_keepsRawBody() throws IOException {
        String body = envelope(MESSAGE_ID);

        listener().receive(OneCStream.MKB10, delivery(body), channel);

        verify(ingestService).receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, body);
    }

    @Test
    void leadingUtf8Bom_isStrippedBeforeStorage() throws IOException {
        String body = envelope(MESSAGE_ID);
        byte[] withBom = ("﻿" + body).getBytes(StandardCharsets.UTF_8);

        listener().receive(OneCStream.MKB10, delivery(QUEUE, withBom), channel);

        verify(ingestService).receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, body);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void storageOutage_pausesConsumersAndLeavesMessageUnanswered() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("база недоступна",
                        new SQLException("connection refused", CONNECTION_FAILURE)));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(consumers).pause(new StorageOutage(CONNECTION_FAILURE, DataAccessResourceFailureException.class.getName()));
        verifyNoInteractions(channel);
        assertThat(outcomes()).containsExactly("storage_outage");
    }

    @Test
    void lockTimeout_pausesConsumers() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("lock timeout", new SQLException("lock", "55P03")));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(consumers).pause(new StorageOutage("55P03", CannotAcquireLockException.class.getName()));
        verifyNoInteractions(channel);
    }

    @Test
    void commitFailureWithoutSqlState_pausesConsumers() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new TransactionSystemException("коммит не удался"));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(consumers).pause(new StorageOutage("нет", TransactionSystemException.class.getName()));
        verifyNoInteractions(channel);
    }

    @Test
    void poisonBody_isQuarantinedWithReasonAndAcknowledged() throws IOException {
        byte[] raw = ("﻿" + envelope(MESSAGE_ID)).getBytes(StandardCharsets.UTF_8);
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(poison());
        when(quarantineService.quarantine(any(), any())).thenReturn(true);

        listener().receive(OneCStream.MKB10, delivery(QUEUE, raw), channel);

        ArgumentCaptor<RejectedMessage> rejected = ArgumentCaptor.forClass(RejectedMessage.class);
        verify(quarantineService).quarantine(rejected.capture(), eq(UNTRANSLATABLE_CHARACTER));
        assertThat(rejected.getValue().messageId()).isEqualTo(MESSAGE_ID);
        assertThat(rejected.getValue().stream()).isEqualTo(OneCStream.MKB10);
        assertThat(rejected.getValue().sourceQueue()).isEqualTo(QUEUE);
        assertThat(rejected.getValue().body()).isEqualTo(raw);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(consumers);
        assertThat(outcomes()).containsExactly("quarantined");
    }

    @Test
    void poisonBody_isAcknowledgedOnlyAfterQuarantine() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(poison());
        when(quarantineService.quarantine(any(), any())).thenReturn(true);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        InOrder quarantineThenBroker = inOrder(quarantineService, channel);
        quarantineThenBroker.verify(quarantineService).quarantine(any(), eq(UNTRANSLATABLE_CHARACTER));
        quarantineThenBroker.verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void redeliveredQuarantinedMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(poison());
        when(quarantineService.quarantine(any(), any())).thenReturn(false);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void quarantineWriteFailure_pausesConsumersWithoutAcknowledgement() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(poison());
        when(quarantineService.quarantine(any(), any())).thenThrow(
                new DataAccessResourceFailureException("база недоступна", new SQLException("lost", CONNECTION_FAILURE)));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(consumers).pause(new StorageOutage(CONNECTION_FAILURE, DataAccessResourceFailureException.class.getName()));
        verifyNoInteractions(channel);
        assertThat(outcomes()).containsExactly("storage_outage");
    }

    @Test
    void concurrentQuarantineOfTheSameMessage_isAcknowledged() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(poison());
        when(quarantineService.quarantine(any(), any())).thenThrow(duplicateRace());

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(consumers);
        assertThat(outcomes()).containsExactly("quarantined");
    }

    @Test
    void duplicateRace_returnsMessageToQueueAfterPause() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(duplicateRace());

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verifyNoInteractions(consumers, quarantineService);
        assertThat(outcomes()).containsExactly("returned");
    }

    @Test
    void leadershipLostBeforeStoring_returnsMessageStopsConsumersAndStepsDownWithoutPause() throws IOException {
        when(ingestService.receive(any(), any(), any(), any()))
                .thenThrow(new LeadershipLostException("0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c"));

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        InOrder returnedThenStopped = inOrder(channel, consumers, leadership);
        returnedThenStopped.verify(channel).basicNack(DELIVERY_TAG, false, true);
        returnedThenStopped.verify(consumers).stopConsumers();
        returnedThenStopped.verify(leadership).stepDown();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(consumers, never()).pause(any());
        verifyNoInteractions(quarantineService);
        assertThat(outcomes()).containsExactly("returned");
    }

    @Test
    void unreadableEnvelope_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery("{\"data\":{},\"metadata\":{}}"), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
        assertThat(outcomes()).containsExactly("unidentified");
    }

    @Test
    void nonCanonicalMessageId_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery(envelope("1-2-3-4-5")), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
    }

    @Test
    void zeroMessageId_isAcknowledgedAndDropped() throws IOException {
        listener().receive(OneCStream.MKB10, delivery(envelope("00000000-0000-0000-0000-000000000000")), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(ingestService);
    }

    @Test
    void priceListFinal_isStoredWithItsOwnQueue() throws IOException {
        String queue = "1c-usk_to_rvl-online_pricelists-contragents-changes-final";

        listener().receive(OneCStream.PRICE_LISTS, delivery(queue, envelope(MESSAGE_ID)), channel);

        verify(ingestService).receive(eq(MESSAGE_ID), eq(OneCStream.PRICE_LISTS), eq(queue), any());
    }

    @Test
    void storedMessage_isAcknowledgedOnlyAfterItIsStored() throws IOException {
        when(ingestService.receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any())).thenReturn(true);

        listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        InOrder storageThenBroker = inOrder(ingestService, channel);
        storageThenBroker.verify(ingestService).receive(eq(MESSAGE_ID), eq(OneCStream.MKB10), eq(QUEUE), any());
        storageThenBroker.verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void duplicateRace_returnsMessageOnlyAfterThePause() throws IOException {
        Duration backoff = Duration.ofMillis(50);
        AtomicLong failedAt = new AtomicLong();
        AtomicLong returnedAt = new AtomicLong();
        when(ingestService.receive(any(), any(), any(), any())).thenAnswer(invocation -> {
            failedAt.set(System.nanoTime());
            throw duplicateRace();
        });
        doAnswer(invocation -> {
            returnedAt.set(System.nanoTime());
            return null;
        }).when(channel).basicNack(DELIVERY_TAG, false, true);

        listener(backoff).receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel);

        assertThat(returnedAt.get()).as("сообщение возвращено в очередь").isNotZero();
        assertThat(Duration.ofNanos(returnedAt.get() - failedAt.get())).isGreaterThanOrEqualTo(backoff);
    }

    @Test
    void interruptedPause_returnsMessageWithoutWaitingOutTheBackoff() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenThrow(duplicateRace());
        OneCQueueListener listener = listener(Duration.ofHours(1));
        Message message = delivery(envelope(MESSAGE_ID));

        boolean interruptKept = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Thread.currentThread().interrupt();
            listener.receive(OneCStream.MKB10, message, channel);
            return Thread.interrupted();
        });

        assertThat(interruptKept).isTrue();
        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    void cyrillicBody_isStoredAsSent() throws IOException {
        String body = """
                {"data":{"code":"A00","name":"Холера"},\
                "metadata":{"message-id":"%s","producer":"1c-usk"}}"""
                .formatted(MESSAGE_ID);

        listener().receive(OneCStream.MKB10, delivery(body), channel);

        verify(ingestService).receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, body);
    }

    @Test
    void acknowledgementIoFailure_isNotTakenForStorageFailure() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenReturn(true);
        doThrow(new IOException("канал закрыт")).when(channel).basicAck(DELIVERY_TAG, false);

        assertThatIOException().isThrownBy(
                () -> listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel));

        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(consumers, quarantineService);
    }

    @Test
    void acknowledgementChannelClosed_isNotTakenForStorageFailure() throws IOException {
        when(ingestService.receive(any(), any(), any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("Channel closed; cannot ack/nack")).when(channel).basicAck(DELIVERY_TAG, false);

        assertThatIllegalStateException().isThrownBy(
                () -> listener().receive(OneCStream.MKB10, delivery(envelope(MESSAGE_ID)), channel));

        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(consumers, quarantineService);
    }

    private OneCQueueListener listener() {
        return listener(Duration.ofMillis(1));
    }

    private OneCQueueListener listener(Duration duplicateDeliveryBackoff) {
        return listener(duplicateDeliveryBackoff, metrics);
    }

    private OneCQueueListener listener(Duration duplicateDeliveryBackoff, OneCIngestMetrics ingestMetrics) {
        return new OneCQueueListener(
                new OneCEnvelopeParser(OneCJson.mapper()),
                ingestService,
                new OneCStorageFailures(quarantineService, consumers,
                        new IngestProperties(duplicateDeliveryBackoff, STORAGE_CHECK_INTERVAL), ingestMetrics,
                        leadership),
                ingestMetrics);
    }

    private static final class FailingMeterRegistry extends SimpleMeterRegistry {

        @Override
        protected Counter newCounter(Meter.Id id) {
            return new FailingCounter(id);
        }
    }

    private record FailingCounter(Meter.Id getId) implements Counter {

        @Override
        public void increment(double amount) {
            throw new IllegalStateException("реестр метрик недоступен");
        }

        @Override
        public double count() {
            return 0;
        }
    }

    private List<String> outcomes() {
        return meters.find(OneCIngestMetrics.DELIVERIES).tag("stream", "MKB10").tag("queue", QUEUE).counters().stream()
                .flatMap(counter -> Collections.nCopies((int) counter.count(), counter.getId().getTag("outcome")).stream())
                .toList();
    }

    private static RuntimeException poison() {
        return new DataIntegrityViolationException("unsupported Unicode escape sequence",
                new SQLException("unsupported Unicode escape sequence", UNTRANSLATABLE_CHARACTER));
    }

    private static RuntimeException duplicateRace() {
        return new DataIntegrityViolationException("duplicate key", new SQLException("duplicate key", UNIQUE_VIOLATION));
    }

    private static String envelope(UUID messageId) {
        return envelope(messageId.toString());
    }

    private static String envelope(String messageId) {
        return """
                {"data":{"guid":"1a2b3c4d-0000-4000-8000-000000000001"},\
                "metadata":{"message-id":"%s","producer":"1c-usk"}}"""
                .formatted(messageId);
    }

    private static Message delivery(String body) {
        return delivery(QUEUE, body);
    }

    private static Message delivery(String queue, String body) {
        return delivery(queue, body.getBytes(StandardCharsets.UTF_8));
    }

    private static Message delivery(String queue, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setConsumerQueue(queue);
        return new Message(body, properties);
    }
}
