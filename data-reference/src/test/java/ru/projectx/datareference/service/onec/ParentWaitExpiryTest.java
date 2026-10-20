package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.entity.inbound.AwaitedParent;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.ParentKind;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.ParentWaitRepository;
import ru.projectx.datareference.service.leadership.LeadershipFence;
import ru.projectx.datareference.service.leadership.LeadershipLostException;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;
import ru.projectx.datareference.support.ImportSettings;

class ParentWaitExpiryTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofHours(24);
    private static final int BATCH_SIZE = 7;
    private static final UUID CID = UUID.fromString("7d3e9a10-4b2c-4f8e-9a61-0c5b8e2f4d17");
    private static final AwaitedParent SANATORIUM =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62"));
    private static final AwaitedParent SERVICE =
            new AwaitedParent(ParentKind.CONTRAGENT_SERVICE, UUID.fromString("4b7d1f9a-3c6e-4d28-9a05-b8e2c1f7d346"));

    private final LeadershipFence fence = mock(LeadershipFence.class);
    private final ParentWaitRepository waits = mock(ParentWaitRepository.class);
    private final InboundMessageRepository inbound = mock(InboundMessageRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ParentWaitExpiry expiry = new ParentWaitExpiry(waits, inbound, new InboundTransitionMetrics(registry),
            ImportSettings.defaults().batchSize(BATCH_SIZE).parentWaitTimeout(TIMEOUT).build(),
            Clock.fixed(NOW, ZoneOffset.UTC), fence);

    @Test
    void instanceThatLostTheLeaderLock_expiresNothing() {
        doThrow(new LeadershipLostException("0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c")).when(fence).holdUntilCommit();

        assertThatThrownBy(expiry::expireOverdue).isInstanceOf(LeadershipLostException.class);

        verifyNoInteractions(waits, inbound);
    }

    @Test
    void overdueMessage_isRejectedNamingItsParent_andReturnedForTheJournal() {
        InboundMessage contract = waiting(OneCStream.LPU_CONTRACTS, SANATORIUM);
        when(waits.findExpiredUnresolved(NOW.minus(TIMEOUT), Limit.of(BATCH_SIZE))).thenReturn(List.of(contract));

        List<ExpiredParentWait> expired = expiry.expireOverdue();

        assertThat(contract.outcome().status()).isEqualTo(InboundStatus.FAILED);
        assertThat(contract.outcome().lastError()).isEqualTo(SANATORIUM.expiredReason());
        assertThat(expired).containsExactly(new ExpiredParentWait(OneCStream.LPU_CONTRACTS, SANATORIUM, null));
        assertThat(failedTransitions("LPU_CONTRACTS")).isEqualTo(1);
        verify(inbound, never()).findByCorrelationId(any());
    }

    @Test
    void overduePartOfATransfer_rejectsTheWholeTransferOnce() {
        InboundMessage batch = waitingPart(null);
        InboundMessage finalMessage = waitingPart(1);
        when(waits.findExpiredUnresolved(NOW.minus(TIMEOUT), Limit.of(BATCH_SIZE)))
                .thenReturn(List.of(batch, finalMessage));
        when(inbound.findByCorrelationId(CID)).thenReturn(List.of(batch, finalMessage));

        List<ExpiredParentWait> expired = expiry.expireOverdue();

        assertThat(List.of(batch, finalMessage)).allSatisfy(part -> {
            assertThat(part.outcome().status()).isEqualTo(InboundStatus.FAILED);
            assertThat(part.outcome().lastError()).isEqualTo(SERVICE.expiredReason());
        });
        assertThat(expired).hasSize(2).allSatisfy(message -> assertThat(message)
                .isEqualTo(new ExpiredParentWait(OneCStream.PRICE_LISTS, SERVICE, CID)));
        assertThat(failedTransitions("PRICE_LISTS")).isEqualTo(2);
    }

    @Test
    void partWithRecordedOutcome_isLeftAsItIs() {
        InboundMessage waitingPart = waitingPart(1);
        InboundMessage closedPart = waitingPart(null);
        closedPart.markSkipped("передача пропущена", NOW);
        when(waits.findExpiredUnresolved(NOW.minus(TIMEOUT), Limit.of(BATCH_SIZE))).thenReturn(List.of(waitingPart));
        when(inbound.findByCorrelationId(CID)).thenReturn(List.of(waitingPart, closedPart));

        assertThat(expiry.expireOverdue()).hasSize(1);
        assertThat(closedPart.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
    }

    @Test
    void nothingOverdue_rejectsNothing() {
        assertThat(expiry.expireOverdue()).isEmpty();
    }

    private double failedTransitions(String stream) {
        return registry.get(InboundTransitionMetrics.TRANSITIONS)
                .tags("stream", stream, "status", "FAILED").counter().count();
    }

    private static InboundMessage waiting(OneCStream stream, AwaitedParent parent) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), stream, "очередь", NOW.minus(TIMEOUT)), "{}");
        message.markAwaitingParent(parent, null, NOW.minus(TIMEOUT));
        return message;
    }

    private static InboundMessage waitingPart(Integer batchCount) {
        InboundMessage part = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", NOW.minus(TIMEOUT)), "{}");
        part.markBuffered(CID, batchCount, 1);
        part.markAwaitingParent(SERVICE, null, NOW.minus(TIMEOUT));
        return part;
    }
}
