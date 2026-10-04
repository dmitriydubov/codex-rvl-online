package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.InboxRepository;

/**
 * Проверка доступности читает обе таблицы приёма в одной транзакции только для чтения; отказ любой из них —
 * база недоступна. Как читается заблокированная таблица, проверяет приёмка на настоящей базе.
 */
@ExtendWith(MockitoExtension.class)
class IngestStorageProbeTest {

    private static final UUID NEVER_STORED = new UUID(0, 0);

    @Mock
    private InboxRepository inbox;

    @Mock
    private InboundMessageRepository rawMessages;

    @Mock
    private PlatformTransactionManager transactions;

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
    void noConnection_isUnavailable() {
        when(transactions.getTransaction(any())).thenThrow(
                new org.springframework.transaction.CannotCreateTransactionException("нет соединения"));

        assertThat(probe().storageAvailable()).isFalse();
    }

    private IngestStorageProbe probe() {
        return new IngestStorageProbe(inbox, rawMessages, transactions);
    }
}
