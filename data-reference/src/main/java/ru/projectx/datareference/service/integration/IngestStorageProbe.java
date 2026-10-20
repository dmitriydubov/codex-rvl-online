package ru.projectx.datareference.service.integration;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.InboxRepository;
import ru.projectx.datareference.service.database.SqlStates;

@Component
public class IngestStorageProbe {

    private static final Logger log = LoggerFactory.getLogger(IngestStorageProbe.class);

    private static final UUID NEVER_STORED = new UUID(0, 0);

    private final InboxRepository inbox;
    private final InboundMessageRepository rawMessages;
    private final TransactionOperations readOnly;

    public IngestStorageProbe(
            InboxRepository inbox, InboundMessageRepository rawMessages, PlatformTransactionManager transactions) {

        this.inbox = inbox;
        this.rawMessages = rawMessages;
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setReadOnly(true);
        this.readOnly = template;
    }

    public boolean storageAvailable() {
        return outage().isEmpty();
    }

    public Optional<StorageOutage> outage() {
        try {
            readOnly.executeWithoutResult(status -> touchIngestTables());
            return Optional.empty();
        }
        catch (RuntimeException unavailable) {
            log.info("таблицы приёма из 1С:УСК недоступны: {} SQLSTATE {}",
                    unavailable.getClass().getName(), SqlStates.deepestOrNone(unavailable));
            return Optional.of(new StorageOutage(SqlStates.deepestOrNone(unavailable), unavailable.getClass().getName()));
        }
    }

    private void touchIngestTables() {
        boolean inInbox = inbox.existsById(NEVER_STORED);
        boolean inRaw = rawMessages.existsById(NEVER_STORED);
        if (inInbox || inRaw) {
            log.warn("в таблицах приёма лежит сообщение с нулевым GUID, которого приём не пропускает");
        }
    }
}
