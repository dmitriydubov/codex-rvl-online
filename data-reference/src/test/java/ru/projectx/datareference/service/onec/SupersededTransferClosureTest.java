package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.PriceListFinalization;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;
import ru.projectx.datareference.service.FinalRejectionJournal;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Защищает закрытие зависшей передачи прайса: она пропускается, только если по её услуге уже применена передача,
 * которую она по порядку не может обогнать. Закрыть лишнее значит потерять прайс, который ещё мог прийти; не закрыть
 * — оставить передачу в буфере и в предупреждении навсегда.
 */
class SupersededTransferClosureTest {

    private static final UUID SERVICE = UUID.fromString("2a6f0c3e-9b18-4d57-8e41-c5d2a7f9b063");
    private static final UUID STUCK_CID = UUID.fromString("7d3e9a10-4b2c-4f8e-9a61-0c5b8e2f4d17");
    private static final UUID APPLIED_CID = UUID.fromString("8e4fab21-5c3d-4a9f-8b72-1d6c9f3a5e28");
    private static final Instant APPLIED_FINAL = Instant.parse("2026-03-14T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-14T11:00:00Z");
    private static final String REASON =
            "по услуге " + SERVICE + " уже применена более новая передача " + APPLIED_CID;

    private final InboundMessageRepository inbound = mock(InboundMessageRepository.class);
    private final PriceListFinalizationRepository finalizations = mock(PriceListFinalizationRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<InboundMessage> parts = new ArrayList<>();

    @Test
    void stuckTransferOlderThanTheApplied_isSkippedWhole_withReason() {
        InboundMessage first = part(APPLIED_FINAL.minusSeconds(60), null);
        InboundMessage second = part(APPLIED_FINAL.minusSeconds(30), null);
        stuckTransferOfService(SERVICE.toString());
        appliedFinal();

        boolean closed = closure().closeIfSuperseded(STUCK_CID);

        assertThat(closed).isTrue();
        assertThat(List.of(first, second)).allSatisfy(message -> {
            assertThat(message.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
            assertThat(message.outcome().lastError()).isEqualTo(REASON);
            assertThat(message.outcome().processedAt()).isEqualTo(NOW);
        });
        verify(inbound).save(first);
        verify(inbound).save(second);
        assertThat(meters.get(InboundTransitionMetrics.TRANSITIONS).tag("stream", OneCStream.PRICE_LISTS.name())
                .tag("status", InboundStatus.SKIPPED.name()).counter().count())
                .isEqualTo(2.0);
    }

    /** Строка услуги блокируется раньше, чем читаются части: иначе решение принималось бы по устаревшим статусам. */
    @Test
    void serviceRowIsLocked_beforeTransferPartsAreRead() {
        part(APPLIED_FINAL.minusSeconds(60), null);
        stuckTransferOfService(SERVICE.toString());
        appliedFinal();

        closure().closeIfSuperseded(STUCK_CID);

        InOrder steps = inOrder(finalizations, inbound);
        steps.verify(finalizations).lockByService(SERVICE);
        steps.verify(inbound).findByCorrelationId(STUCK_CID);
    }

    /** Часть пришла после финала применённой, а своего финала нет: передача ещё может стать последней. */
    @Test
    void stuckTransferThatMayStillBecomeTheLast_staysInBuffer() {
        InboundMessage late = part(APPLIED_FINAL.plusSeconds(60), null);
        stuckTransferOfService(SERVICE.toString());
        appliedFinal();

        assertThat(closure().closeIfSuperseded(STUCK_CID)).isFalse();
        assertThat(late.outcome().status()).isEqualTo(InboundStatus.BUFFERED);
        verify(inbound, never()).save(any());
    }

    /** Только части в буфере: исход остальных уже записан, и закрытие их не трогает. */
    @Test
    void partsWithOutcome_areLeftAsTheyAre() {
        InboundMessage buffered = part(APPLIED_FINAL.minusSeconds(60), null);
        InboundMessage failed = part(APPLIED_FINAL.minusSeconds(30), null);
        failed.markFailed("лишний пакет");
        stuckTransferOfService(SERVICE.toString());
        appliedFinal();

        closure().closeIfSuperseded(STUCK_CID);

        assertThat(buffered.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
        assertThat(failed.outcome().status()).isEqualTo(InboundStatus.FAILED);
        verify(inbound, never()).save(failed);
    }

    @Test
    void serviceWithoutAppliedTransfer_leavesStuckTransferInBuffer() {
        InboundMessage stuck = part(APPLIED_FINAL, null);
        stuckTransferOfService(SERVICE.toString());
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.empty());

        assertThat(closure().closeIfSuperseded(STUCK_CID)).isFalse();
        assertThat(stuck.outcome().status()).isEqualTo(InboundStatus.BUFFERED);
    }

    /** Услугу не узнать — сравнивать не с чем: передача остаётся, о ней предупредит проверка зависших. */
    @Test
    void transferWithoutReadableService_staysInBuffer() {
        when(inbound.findBufferedTransferService(STUCK_CID)).thenReturn(Optional.of("не GUID"));

        assertThat(closure().closeIfSuperseded(STUCK_CID)).isFalse();
        verify(finalizations, never()).lockByService(any());
    }

    @Test
    void transferNoLongerInBuffer_isNotClosed() {
        when(inbound.findBufferedTransferService(STUCK_CID)).thenReturn(Optional.empty());

        assertThat(closure().closeIfSuperseded(STUCK_CID)).isFalse();
        verify(finalizations, never()).lockByService(any());
    }

    private void stuckTransferOfService(String service) {
        when(inbound.findBufferedTransferService(STUCK_CID)).thenReturn(Optional.of(service));
        when(inbound.findByCorrelationId(STUCK_CID)).thenReturn(parts);
    }

    private void appliedFinal() {
        InboundMessage appliedFinal = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", APPLIED_FINAL), "{}");
        appliedFinal.markBuffered(APPLIED_CID, 1, 1);
        appliedFinal.markProcessed(APPLIED_FINAL);
        UUID finalId = appliedFinal.receipt().messageId();
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.of(PriceListFinalization.of(SERVICE, finalId)));
        when(inbound.findById(finalId)).thenReturn(Optional.of(appliedFinal));
    }

    private InboundMessage part(Instant receivedAt, Integer batchCount) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", receivedAt), "{}");
        message.markBuffered(STUCK_CID, batchCount, 1);
        parts.add(message);
        return message;
    }

    private SupersededTransferClosure closure() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InboundMessageService inboundMessageService = new InboundMessageService(
                inbound, new InboundRetryProperties(List.of(Duration.ofSeconds(30))), clock,
                new InboundTransitionMetrics(meters), new FinalRejectionJournal(), List.of());
        return new SupersededTransferClosure(inbound, new PriceTransferOrder(finalizations, inbound),
                inboundMessageService, clock);
    }
}
