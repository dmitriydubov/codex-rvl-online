package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuarantinedMessageTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0a4f1c0e-7b2d-4e7a-9c1b-2d3e4f5a6b70");
    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-04T12:00:00Z");
    private static final String REASON = "22P05";

    @Test
    void record_keepsEveryFieldOfTheRejectedMessageAndReason() {
        byte[] body = {1, 2, 0, 3};

        QuarantinedMessage quarantined = QuarantinedMessage.of(
                new RejectedMessage(MESSAGE_ID, OneCStream.POLICIES, QUEUE, body), REASON, RECEIVED_AT);

        assertThat(quarantined.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(quarantined.stream()).isEqualTo(OneCStream.POLICIES);
        assertThat(quarantined.sourceQueue()).isEqualTo(QUEUE);
        assertThat(quarantined.body()).contains(body);
        assertThat(quarantined.reason()).isEqualTo(REASON);
        assertThat(quarantined.receivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void body_isCopiedOnWriteAndOnRead() {
        byte[] body = {1, 2, 3};
        QuarantinedMessage quarantined = QuarantinedMessage.of(
                new RejectedMessage(MESSAGE_ID, OneCStream.MKB10, QUEUE, body), REASON, RECEIVED_AT);

        body[0] = 9;
        byte[] read = quarantined.body().orElseThrow();
        read[1] = 9;

        assertThat(quarantined.body().orElseThrow()).containsExactly(1, 2, 3);
    }

    @Test
    void forgetBody_erasesOnlyTheBody() {
        QuarantinedMessage quarantined = QuarantinedMessage.of(
                new RejectedMessage(MESSAGE_ID, OneCStream.MKB10, QUEUE, new byte[] {1}), REASON, RECEIVED_AT);

        quarantined.forgetBody();

        assertThat(quarantined.body()).isEmpty();
        assertThat(quarantined.reason()).isEqualTo(REASON);
        assertThat(quarantined.messageId()).isEqualTo(MESSAGE_ID);
    }
}
