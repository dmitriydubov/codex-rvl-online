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
import ru.projectx.datareference.entity.InboxEntry;
import ru.projectx.datareference.repository.InboxRepository;

@ExtendWith(MockitoExtension.class)
class InboxServiceUnitTest {

    private static final UUID MESSAGE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");

    @Mock
    private InboxRepository repository;

    private InboxService inboxService() {
        return new InboxService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownMessageId_isRecordedWithReceiptTime_andReportedAsNew() {
        when(repository.existsById(MESSAGE_ID)).thenReturn(false);

        boolean isNew = inboxService().registerIfNew(MESSAGE_ID);

        assertThat(isNew).isTrue();
        ArgumentCaptor<InboxEntry> saved = ArgumentCaptor.forClass(InboxEntry.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().messageId()).isEqualTo(MESSAGE_ID);
        assertThat(saved.getValue().receivedAt()).isEqualTo(NOW);
    }

    @Test
    void knownMessageId_isNotRecordedAgain_andReportedAsDuplicate() {
        when(repository.existsById(MESSAGE_ID)).thenReturn(true);

        boolean isNew = inboxService().registerIfNew(MESSAGE_ID);

        assertThat(isNew).isFalse();
        verify(repository, never()).save(any());
    }
}
