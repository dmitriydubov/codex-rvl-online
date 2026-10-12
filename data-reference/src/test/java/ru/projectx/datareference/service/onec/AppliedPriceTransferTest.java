package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;

class AppliedPriceTransferTest {

    private static final UUID SERVICE = UUID.fromString("2a6f0c3e-9b18-4d57-8e41-c5d2a7f9b063");
    private static final UUID APPLIED_CID = UUID.fromString("8e4fab21-5c3d-4a9f-8b72-1d6c9f3a5e28");
    private static final UUID STUCK_CID = UUID.fromString("7d3e9a10-4b2c-4f8e-9a61-0c5b8e2f4d17");
    private static final Instant APPLIED_FINAL = Instant.parse("2026-03-14T09:00:00Z");
    private static final Instant BEFORE = APPLIED_FINAL.minusMillis(1);
    private static final Instant AFTER = APPLIED_FINAL.plusMillis(1);

    private final AppliedPriceTransfer applied = new AppliedPriceTransfer(SERVICE, APPLIED_CID, APPLIED_FINAL);

    @Test
    void transferFinalizedEarlier_isOlder() {
        assertThat(applied.isNewerThan(BEFORE)).isTrue();
        assertThat(applied.isNewerThan(AFTER)).isFalse();
    }

    @Test
    void transferFinalizedAtTheSameMoment_isNotOlder() {
        assertThat(applied.isNewerThan(APPLIED_FINAL)).isFalse();
    }

    @Test
    void reason_namesTheServiceAndTheAppliedTransfer() {
        assertThat(applied.reason())
                .isEqualTo("по услуге " + SERVICE + " уже применена более новая передача " + APPLIED_CID);
    }

    @Test
    void stuckTransferReceivedEntirelyBeforeAppliedFinal_isSuperseded() {
        assertThat(applied.supersedes(List.of(batch(BEFORE), batch(BEFORE.minusSeconds(60))))).isTrue();
    }

    @Test
    void stuckTransferWithPartAfterAppliedFinal_andNoFinalOfItsOwn_isNotSuperseded() {
        assertThat(applied.supersedes(List.of(batch(BEFORE), batch(AFTER)))).isFalse();
        assertThat(applied.supersedes(List.of(batch(APPLIED_FINAL)))).isFalse();
    }

    @Test
    void stuckTransferWithOlderFinalOfItsOwn_isSuperseded_evenWithLaterBatch() {
        assertThat(applied.supersedes(List.of(finalMessage(BEFORE), batch(AFTER)))).isTrue();
    }

    @Test
    void stuckTransferWithNewerFinalOfItsOwn_isNotSuperseded() {
        assertThat(applied.supersedes(List.of(finalMessage(AFTER), batch(BEFORE)))).isFalse();
        assertThat(applied.supersedes(List.of(finalMessage(APPLIED_FINAL), batch(BEFORE)))).isFalse();
    }

    @Test
    void transferWithoutBufferedParts_isNotSuperseded() {
        assertThat(applied.supersedes(List.of())).isFalse();
    }

    private static InboundMessage batch(Instant receivedAt) {
        return part(receivedAt, null);
    }

    private static InboundMessage finalMessage(Instant receivedAt) {
        return part(receivedAt, 1);
    }

    private static InboundMessage part(Instant receivedAt, Integer batchCount) {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", receivedAt), "{}");
        message.markBuffered(STUCK_CID, batchCount, 1);
        return message;
    }
}
