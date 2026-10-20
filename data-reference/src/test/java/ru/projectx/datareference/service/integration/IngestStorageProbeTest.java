package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.InboxRepository;

@ExtendWith(MockitoExtension.class)
class IngestStorageProbeTest {

    private static final UUID NEVER_STORED = new UUID(0, 0);
    private static final String NIL_GUID_WARNING = "нулевым GUID";

    @Mock
    private InboxRepository inbox;

    @Mock
    private InboundMessageRepository rawMessages;

    @Mock
    private PlatformTransactionManager transactions;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLog() {
        logged.start();
        probeLog().addAppender(logged);
        probeLog().setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLog() {
        probeLog().detachAppender(logged);
        probeLog().setLevel(null);
        logged.stop();
    }

    @Test
    void bothTablesReadable_isAvailable_insideOneReadOnlyTransaction() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(probe().storageAvailable()).isTrue();

        verify(inbox).existsById(NEVER_STORED);
        verify(rawMessages).existsById(NEVER_STORED);
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        verify(transactions).commit(any());
    }

    @Test
    void bothTablesWithoutNilGuid_logNothing() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(probe().storageAvailable()).isTrue();

        assertThat(logged.list).isEmpty();
    }

    @Test
    void nilGuidInInbox_isAvailableButWarns() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(inbox.existsById(NEVER_STORED)).thenReturn(true);

        assertThat(probe().storageAvailable()).isTrue();

        assertThat(onlyWarning().getFormattedMessage()).contains(NIL_GUID_WARNING);
    }

    @Test
    void nilGuidInRawMessages_isAvailableButWarns() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(rawMessages.existsById(NEVER_STORED)).thenReturn(true);

        assertThat(probe().storageAvailable()).isTrue();

        assertThat(onlyWarning().getFormattedMessage()).contains(NIL_GUID_WARNING);
    }

    @Test
    void rawTableLocked_isUnavailable() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(rawMessages.existsById(NEVER_STORED))
                .thenThrow(new CannotAcquireLockException("lock", new SQLException("lock", "55P03")));

        assertThat(probe().storageAvailable()).isFalse();
    }

    @Test
    void inboxTableLocked_isUnavailable() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(inbox.existsById(NEVER_STORED))
                .thenThrow(new CannotAcquireLockException("lock", new SQLException("lock", "55P03")));

        assertThat(probe().storageAvailable()).isFalse();
    }

    @Test
    void lockedTable_isAnOutageNamingItsCodeAndType() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(inbox.existsById(NEVER_STORED))
                .thenThrow(new CannotAcquireLockException("lock", new SQLException("lock", "55P03")));

        assertThat(probe().outage())
                .hasValue(new StorageOutage("55P03", CannotAcquireLockException.class.getName()));
    }

    @Test
    void readableTables_areNoOutage() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(probe().outage()).isEmpty();
    }

    @Test
    void noConnection_isUnavailable() {
        when(transactions.getTransaction(any())).thenThrow(
                new org.springframework.transaction.CannotCreateTransactionException("нет соединения"));

        assertThat(probe().storageAvailable()).isFalse();
    }

    private ILoggingEvent onlyWarning() {
        assertThat(logged.list).hasSize(1);
        ILoggingEvent event = logged.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        return event;
    }

    private IngestStorageProbe probe() {
        return new IngestStorageProbe(inbox, rawMessages, transactions);
    }

    private static Logger probeLog() {
        return (Logger) LoggerFactory.getLogger(IngestStorageProbe.class);
    }
}
