package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.QuarantinedMessage;
import ru.projectx.datareference.entity.RejectedMessage;
import ru.projectx.datareference.repository.QuarantineRepository;

@ExtendWith(MockitoExtension.class)
class QuarantineServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString("0a4f1c0e-7b2d-4e7a-9c1b-2d3e4f5a6b70");
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";

    @Mock
    private QuarantineRepository repository;

    @Test
    void newMessage_isSavedWithReasonAndClockInstant() {
        when(repository.existsById(MESSAGE_ID)).thenReturn(false);
        RejectedMessage rejected = new RejectedMessage(MESSAGE_ID, OneCStream.MKB10, QUEUE, new byte[] {7});

        boolean quarantined = service().quarantine(rejected, "22P05");

        assertThat(quarantined).isTrue();
        ArgumentCaptor<QuarantinedMessage> saved = ArgumentCaptor.forClass(QuarantinedMessage.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().messageId()).isEqualTo(MESSAGE_ID);
        assertThat(saved.getValue().sourceQueue()).isEqualTo(QUEUE);
        assertThat(saved.getValue().body().orElseThrow()).containsExactly(7);
        assertThat(saved.getValue().reason()).isEqualTo("22P05");
        assertThat(saved.getValue().receivedAt()).isEqualTo(NOW);
    }

    @Test
    void alreadyQuarantinedMessage_isNotSavedAgain() {
        when(repository.existsById(MESSAGE_ID)).thenReturn(true);

        boolean quarantined = service().quarantine(
                new RejectedMessage(MESSAGE_ID, OneCStream.MKB10, QUEUE, new byte[] {7}), "22P05");

        assertThat(quarantined).isFalse();
        verify(repository, never()).save(any());
    }

    private QuarantineService service() {
        return new QuarantineService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
