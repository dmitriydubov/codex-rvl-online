package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.entity.inbound.PriceListFinalization;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;

class PriceTransferOrderTest {

    private static final UUID SERVICE = UUID.fromString("2a6f0c3e-9b18-4d57-8e41-c5d2a7f9b063");
    private static final UUID APPLIED_CID = UUID.fromString("8e4fab21-5c3d-4a9f-8b72-1d6c9f3a5e28");
    private static final UUID CID = UUID.fromString("7d3e9a10-4b2c-4f8e-9a61-0c5b8e2f4d17");
    private static final Instant APPLIED_AT = Instant.parse("2026-03-14T09:00:00Z");

    private final PriceListFinalizationRepository finalizations = mock(PriceListFinalizationRepository.class);
    private final InboundMessageRepository inbound = mock(InboundMessageRepository.class);
    private final PriceTransferOrder order = new PriceTransferOrder(finalizations, inbound);

    @Test
    void firstTransferOfService_isApplied_andBecomesTheLast() {
        InboundMessage finalMessage = finalMessage(CID, APPLIED_AT);
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.empty());

        ImportResult result = order.applyInOrder(SERVICE, finalMessage, ImportResult::applied);

        assertThat(result).isEqualTo(ImportResult.applied());
        verify(finalizations).recordApplied(SERVICE, finalMessage.receipt().messageId());
    }

    @Test
    void transferFinalizedAfterTheApplied_replacesPrices_andBecomesTheLast() {
        appliedFinal(APPLIED_AT);
        InboundMessage finalMessage = finalMessage(CID, APPLIED_AT.plusSeconds(1));

        ImportResult result = order.applyInOrder(SERVICE, finalMessage, ImportResult::applied);

        assertThat(result).isEqualTo(ImportResult.applied());
        verify(finalizations).recordApplied(SERVICE, finalMessage.receipt().messageId());
    }

    @Test
    void serviceRowIsLocked_beforePricesAreReplaced() {
        appliedFinal(APPLIED_AT);
        AtomicBoolean lockedBeforeReplacing = new AtomicBoolean();

        order.applyInOrder(SERVICE, finalMessage(CID, APPLIED_AT.plusSeconds(1)), () -> {
            lockedBeforeReplacing.set(mockingDetails(finalizations).getInvocations().stream()
                    .anyMatch(call -> call.getMethod().getName().equals("lockByService")));
            return ImportResult.applied();
        });

        assertThat(lockedBeforeReplacing).isTrue();
    }

    @Test
    void transferFinalizedBeforeTheApplied_isSkipped_andPricesStay() {
        appliedFinal(APPLIED_AT);
        AtomicBoolean replaced = new AtomicBoolean();

        ImportResult result = order.applyInOrder(SERVICE, finalMessage(CID, APPLIED_AT.minusSeconds(1)), () -> {
            replaced.set(true);
            return ImportResult.applied();
        });

        assertThat(result).isEqualTo(ImportResult.skipped(
                "по услуге " + SERVICE + " уже применена более новая передача " + APPLIED_CID));
        assertThat(replaced).isFalse();
        verify(finalizations, never()).recordApplied(any(), any());
    }

    @Test
    void transferFinalizedAtTheSameMoment_isApplied() {
        appliedFinal(APPLIED_AT);
        InboundMessage finalMessage = finalMessage(CID, APPLIED_AT);

        assertThat(order.applyInOrder(SERVICE, finalMessage, ImportResult::applied)).isEqualTo(ImportResult.applied());
        verify(finalizations).recordApplied(SERVICE, finalMessage.receipt().messageId());
    }

    @Test
    void skippedReplacement_doesNotBecomeTheLast() {
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.empty());

        ImportResult result = order.applyInOrder(SERVICE, finalMessage(CID, APPLIED_AT),
                () -> ImportResult.skipped("прайс по услуге вне справочника"));

        assertThat(result).isEqualTo(ImportResult.skipped("прайс по услуге вне справочника"));
        verify(finalizations, never()).recordApplied(any(), any());
    }

    @Test
    void appliedTransfer_isReadFromItsFinalMessage() {
        appliedFinal(APPLIED_AT);

        assertThat(order.lockApplied(SERVICE))
                .contains(new AppliedPriceTransfer(SERVICE, APPLIED_CID, APPLIED_AT));
    }

    @Test
    void serviceWithoutAppliedTransfer_hasNoAppliedTransfer() {
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.empty());

        assertThat(order.lockApplied(SERVICE)).isEmpty();
    }

    @Test
    void missingFinalMessageOfAppliedTransfer_isAnError() {
        UUID finalId = UUID.randomUUID();
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.of(PriceListFinalization.of(SERVICE, finalId)));
        when(inbound.findById(finalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> order.lockApplied(SERVICE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("нет строки сырья финального сообщения " + finalId + " применённой передачи");
    }

    private PriceListFinalization appliedFinal(Instant receivedAt) {
        InboundMessage appliedFinal = finalMessage(APPLIED_CID, receivedAt);
        UUID finalId = appliedFinal.receipt().messageId();
        PriceListFinalization finalization = PriceListFinalization.of(SERVICE, finalId);
        when(finalizations.lockByService(SERVICE)).thenReturn(Optional.of(finalization));
        when(inbound.findById(finalId)).thenReturn(Optional.of(appliedFinal));
        return finalization;
    }

    private static InboundMessage finalMessage(UUID cid, Instant receivedAt) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", receivedAt), "{}");
        message.markBuffered(cid, 1, 1);
        return message;
    }
}
