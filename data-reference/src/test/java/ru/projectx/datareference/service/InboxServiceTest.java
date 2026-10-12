package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.InboxEntry;
import ru.projectx.datareference.repository.InboxRepository;
import ru.projectx.datareference.support.FixedClockConfig;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class InboxServiceTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private InboxService inboxService;

    @Autowired
    private InboxRepository repository;

    @BeforeEach
    void clearJournal() {
        databaseCleaner.clear();
    }

    @Test
    void newMessageId_isStored() {
        UUID messageId = UUID.randomUUID();

        boolean stored = inboxService.registerIfNew(messageId);

        assertThat(stored).isTrue();
        assertThat(repository.findById(messageId)).isPresent();
    }

    @Test
    void duplicateMessageId_isNotStoredTwice() {
        UUID messageId = UUID.randomUUID();
        inboxService.registerIfNew(messageId);

        boolean storedAgain = inboxService.registerIfNew(messageId);

        assertThat(storedAgain).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void duplicateMessageId_keepsOriginalReceivedAt() {
        UUID messageId = UUID.randomUUID();
        inboxService.registerIfNew(messageId);
        Instant firstReceipt = repository.findById(messageId).orElseThrow().receivedAt();

        inboxService.registerIfNew(messageId);

        assertThat(repository.findById(messageId))
                .map(InboxEntry::receivedAt)
                .contains(firstReceipt);
    }

    @Test
    void receivedAt_isTakenFromClock() {
        UUID messageId = UUID.randomUUID();

        inboxService.registerIfNew(messageId);

        assertThat(repository.findById(messageId))
                .map(InboxEntry::receivedAt)
                .contains(FixedClockConfig.NOW);
    }
}
