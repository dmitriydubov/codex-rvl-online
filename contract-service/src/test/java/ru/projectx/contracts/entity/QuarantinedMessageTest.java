package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuarantinedMessageTest {

    private static final UUID MESSAGE_ID = UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11");
    private static final Instant RECEIVED = Instant.parse("2026-02-10T07:05:00Z");
    private static final String QUEUE = "rvl_requests_to_rvl_contracts_task-changes";

    private final byte[] body = "{\"meta\":{}}".getBytes(StandardCharsets.UTF_8);
    private final QuarantinedMessage quarantined = QuarantinedMessage.of(
            new RejectedMessage(MESSAGE_ID, QUEUE, body), "в теле символ, который база не хранит", RECEIVED);

    @Test
    void quarantinedMessage_keepsKeyQueueReasonAndMoment() {
        assertThat(quarantined.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(quarantined.sourceQueue()).isEqualTo(QUEUE);
        assertThat(quarantined.reason()).isEqualTo("в теле символ, который база не хранит");
        assertThat(quarantined.receivedAt()).isEqualTo(RECEIVED);
    }

    @Test
    void body_isACopyOfTheBytesInBothDirections() {
        body[0] = 'X';

        assertThat(quarantined.body()).contains("{\"meta\":{}}".getBytes(StandardCharsets.UTF_8));
        quarantined.body().orElseThrow()[0] = 'Y';
        assertThat(quarantined.body()).contains("{\"meta\":{}}".getBytes(StandardCharsets.UTF_8));
    }
}
