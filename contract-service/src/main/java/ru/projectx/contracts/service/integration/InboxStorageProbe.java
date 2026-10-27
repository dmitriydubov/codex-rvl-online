package ru.projectx.contracts.service.integration;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.repository.InboxProbeRepository;

@Component
public class InboxStorageProbe {

    private static final Logger log = LoggerFactory.getLogger(InboxStorageProbe.class);

    private static final UUID NEVER_STORED = new UUID(0, 0);

    private final InboxProbeRepository tables;
    private final TransactionOperations transaction;

    public InboxStorageProbe(InboxProbeRepository tables, PlatformTransactionManager transactions) {
        this.tables = tables;
        this.transaction = new TransactionTemplate(transactions);
    }

    public boolean storageAvailable() {
        return outage().isEmpty();
    }

    public Optional<StorageOutage> outage() {
        try {
            transaction.executeWithoutResult(status -> touchInboxTables());
            return Optional.empty();
        }
        catch (RuntimeException unavailable) {
            StorageOutage outage = StorageOutage.of(unavailable);
            log.info("таблицы журнала входящих недоступны: {} SQLSTATE {}", outage.failureType(), outage.sqlState());
            return Optional.of(outage);
        }
    }

    private void touchInboxTables() {
        boolean inInbox = tables.inboxRowExists(NEVER_STORED);
        boolean inQuarantine = tables.quarantineRowExists(NEVER_STORED);
        if (inInbox || inQuarantine) {
            log.warn("в таблицах журнала входящих лежит запись с нулевым GUID, которой приём не пропускает");
        }
    }
}
