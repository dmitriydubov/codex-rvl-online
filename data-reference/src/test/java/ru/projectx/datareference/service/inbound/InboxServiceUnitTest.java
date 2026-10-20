package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.inbound.InboxEntry;
import ru.projectx.datareference.repository.database.DatabaseClockRepository;
import ru.projectx.datareference.repository.inbound.InboxRepository;

@ExtendWith(MockitoExtension.class)
class InboxServiceUnitTest {

    private static final UUID MESSAGE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final Instant DATABASE_MOMENT = Instant.parse("2026-10-20T08:30:00.123456Z");

    @Mock
    private InboxRepository repository;

    @Mock
    private DatabaseClockRepository databaseClock;

    private InboxService inboxService() {
        return new InboxService(repository, databaseClock);
    }

    @Test
    void unknownMessageId_isRecordedWithReceiptTimeOfDatabaseClock_andReportedAsNew() {
        when(repository.existsById(MESSAGE_ID)).thenReturn(false);
        when(databaseClock.currentMoment()).thenReturn(DATABASE_MOMENT);

        boolean isNew = inboxService().registerIfNew(MESSAGE_ID);

        assertThat(isNew).isTrue();
        ArgumentCaptor<InboxEntry> saved = ArgumentCaptor.forClass(InboxEntry.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().messageId()).isEqualTo(MESSAGE_ID);
        assertThat(saved.getValue().receivedAt()).isEqualTo(DATABASE_MOMENT);
    }

    @Test
    void knownMessageId_isNotRecordedAgain_andReportedAsDuplicate() {
        when(repository.existsById(MESSAGE_ID)).thenReturn(true);

        boolean isNew = inboxService().registerIfNew(MESSAGE_ID);

        assertThat(isNew).isFalse();
        verify(repository, never()).save(any());
    }
}
