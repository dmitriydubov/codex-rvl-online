package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ParentWaitRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Защищает правило «новое не затирается старым» для сообщений, которые ждут родителя: применённое сообщение записи
 * закрывает её более старые ожидающие сообщения пропуском с причиной, которая называет запись и более новое
 * сообщение. Без этого старое сообщение, дождавшись родителя, затёрло бы применённое.
 */
class SupersededParentWaitsTest {

    private static final UUID RECORD = UUID.fromString("5f14a8d2-c3e7-4b90-a1d6-9e2b7c4f0a83");
    private static final UUID NEWER_ID = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-8e9f0a1b2c32");
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-06T09:00:00Z");
    private static final Instant NOW = RECEIVED_AT.plusSeconds(600);
    private static final String QUEUE = "1c-usk_to_rvl-online_contragents-contracts-changes";

    private final ParentWaitRepository waits = mock(ParentWaitRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SupersededParentWaits superseded = new SupersededParentWaits(
            waits, new InboundTransitionMetrics(registry), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void olderWaitingMessagesOfTheRecord_areSkippedNamingTheRecordAndTheNewerMessage() {
        InboundReceipt newer = new InboundReceipt(NEWER_ID, OneCStream.LPU_CONTRACTS, QUEUE, RECEIVED_AT);
        InboundMessage older = waiting(UUID.randomUUID());
        InboundMessage oldest = waiting(UUID.randomUUID());
        when(waits.findAwaitingOfRecordBefore(OneCStream.LPU_CONTRACTS, RECORD, RECEIVED_AT))
                .thenReturn(List.of(oldest, older));

        superseded.closeOlderThan(newer, RECORD, "применено");

        String reason = "по записи " + RECORD + " уже применено более новое сообщение " + NEWER_ID;
        assertThat(List.of(oldest, older)).allSatisfy(message -> assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.SKIPPED, 0, null, reason, NOW, null)));
        assertThat(registry.get(InboundTransitionMetrics.TRANSITIONS)
                .tags("stream", "LPU_CONTRACTS", "status", "SKIPPED").counter().count()).isEqualTo(2);
    }

    @Test
    void skippedNewerMessage_isNamedSkippedInTheReason() {
        InboundReceipt newer = new InboundReceipt(NEWER_ID, OneCStream.LPU_CONTRACTS, QUEUE, RECEIVED_AT);
        InboundMessage older = waiting(UUID.randomUUID());
        when(waits.findAwaitingOfRecordBefore(OneCStream.LPU_CONTRACTS, RECORD, RECEIVED_AT)).thenReturn(List.of(older));

        superseded.closeOlderThan(newer, RECORD, "пропущено");

        assertThat(older.outcome().lastError())
                .isEqualTo("по записи " + RECORD + " уже пропущено более новое сообщение " + NEWER_ID);
    }

    private static InboundMessage waiting(UUID messageId) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(messageId, OneCStream.LPU_CONTRACTS, QUEUE, RECEIVED_AT.minusSeconds(60)), "{}");
        message.markAwaitingParent(new AwaitedParent(ParentKind.CONTRAGENT, UUID.randomUUID()), RECORD,
                RECEIVED_AT.minusSeconds(60));
        return message;
    }
}
