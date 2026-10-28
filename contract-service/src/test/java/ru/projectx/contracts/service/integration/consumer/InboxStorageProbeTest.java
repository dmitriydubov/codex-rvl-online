package ru.projectx.contracts.service.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.projectx.contracts.repository.journal.InboxProbeRepository;

class InboxStorageProbeTest {

    private static final UUID NEVER_STORED = new UUID(0, 0);

    private final InboxProbeRepository tables = mock();
    private final PlatformTransactionManager transactions = mock();
    private final SimpleTransactionStatus status = new SimpleTransactionStatus();
    private final InboxStorageProbe probe = new InboxStorageProbe(tables, transactions);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(InboxStorageProbe.class);

    @BeforeEach
    void transactionsOpen() {
        given(transactions.getTransaction(any())).willReturn(status);
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void bothTablesAnswer_storageIsAvailableAndTheTransactionIsCommitted() {
        assertThat(probe.outage()).isEmpty();
        assertThat(probe.storageAvailable()).isTrue();

        verify(tables, org.mockito.Mockito.times(2)).inboxRowExists(NEVER_STORED);
        verify(tables, org.mockito.Mockito.times(2)).quarantineRowExists(NEVER_STORED);
        verify(transactions, org.mockito.Mockito.times(2)).commit(status);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void lockedJournal_isAnOutageWithTheLockTimeoutCode() {
        given(tables.inboxRowExists(NEVER_STORED))
                .willThrow(new CannotAcquireLockException("Тестова", new SQLException("locked", "55P03")));

        assertThat(probe.outage()).contains(new StorageOutage("55P03", CannotAcquireLockException.class.getName()));
        assertThat(probe.storageAvailable()).isFalse();
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.INFO).first()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("недоступны", CannotAcquireLockException.class.getName(), "55P03")
                        .doesNotContain("Тестова"));
    }

    @Test
    void lostConnection_isAnOutageWithItsCode() {
        given(tables.inboxRowExists(NEVER_STORED))
                .willThrow(new DataAccessResourceFailureException("обрыв", new SQLException("io", "08006")));

        assertThat(probe.outage()).contains(new StorageOutage("08006", DataAccessResourceFailureException.class.getName()));
        verify(transactions).rollback(status);
    }

    @Test
    void quarantineTableFailure_isAnOutageToo() {
        given(tables.quarantineRowExists(NEVER_STORED)).willThrow(new IllegalStateException("нет таблицы"));

        assertThat(probe.outage()).contains(new StorageOutage("нет", IllegalStateException.class.getName()));
    }

    @Test
    void rowWithTheNeverStoredKey_isReportedButStorageIsAvailable() {
        given(tables.quarantineRowExists(NEVER_STORED)).willReturn(true);

        assertThat(probe.storageAvailable()).isTrue();
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("нулевым GUID");
        });
    }
}
