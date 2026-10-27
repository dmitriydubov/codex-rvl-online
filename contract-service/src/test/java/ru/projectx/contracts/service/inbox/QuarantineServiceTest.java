package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.entity.QuarantinedMessage;
import ru.projectx.contracts.entity.RejectedMessage;
import ru.projectx.contracts.repository.QuarantineRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

class QuarantineServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11");

    private final QuarantineRepository quarantine = mock();
    private final LeadershipFence fence = mock();
    private final QuarantineService service = new QuarantineService(quarantine, fence, Clock.fixed(NOW, ZoneOffset.UTC));
    private final RejectedMessage rejected = new RejectedMessage(MESSAGE_ID, "task-changes",
            "{\"meta\":{}}".getBytes(StandardCharsets.UTF_8));

    @Test
    void newMessage_isStoredWithReasonAndReceiptTime() {
        given(quarantine.existsById(MESSAGE_ID)).willReturn(false);

        assertThat(service.quarantine(rejected, "SQLSTATE 22P05")).isTrue();

        ArgumentCaptor<QuarantinedMessage> saved = ArgumentCaptor.captor();
        verify(quarantine).save(saved.capture());
        assertThat(saved.getValue().messageId()).isEqualTo(MESSAGE_ID);
        assertThat(saved.getValue().sourceQueue()).isEqualTo("task-changes");
        assertThat(saved.getValue().reason()).isEqualTo("SQLSTATE 22P05");
        assertThat(saved.getValue().receivedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().body()).contains("{\"meta\":{}}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void lostLeaderLock_failsBeforeTheQuarantineIsRead() {
        org.mockito.BDDMockito.willThrow(new LeadershipLostException("0b5f3c1e")).given(fence).holdUntilCommit();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.quarantine(rejected, "SQLSTATE 22P05"))
                .isInstanceOf(LeadershipLostException.class);

        verify(quarantine, never()).existsById(any());
        verify(quarantine, never()).save(any());
    }

    @Test
    void messageAlreadyInQuarantine_isNotStoredAgain() {
        given(quarantine.existsById(MESSAGE_ID)).willReturn(true);

        assertThat(service.quarantine(rejected, "SQLSTATE 22P05")).isFalse();

        verify(quarantine, never()).save(any());
    }
}
