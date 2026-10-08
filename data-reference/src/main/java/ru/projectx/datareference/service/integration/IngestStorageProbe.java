package ru.projectx.datareference.service.integration;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.InboxRepository;
import ru.projectx.datareference.service.SqlStates;

/**
 * Проверка, принимает ли база записи приёма из 1С:УСК: короткой транзакцией ищет строку с нулевым GUID, которой не
 * бывает, в журнале входящих и сырье. Читаются именно таблицы приёма, чтобы проверка упиралась в те же блокировки, что
 * и запись, и потребители не возвращались, пока таблицы держит чужая долгая операция.
 */
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

    /** {@code true}, если обе таблицы приёма прочитались; любой отказ чтения — база недоступна. */
    public boolean storageAvailable() {
        try {
            readOnly.executeWithoutResult(status -> touchIngestTables());
            return true;
        }
        catch (RuntimeException unavailable) {
            // Тип и код, но не текст: текст ошибки базы может нести значения строк.
            log.info("таблицы приёма из 1С:УСК недоступны: {} SQLSTATE {}",
                    unavailable.getClass().getName(), SqlStates.deepestOrNone(unavailable));
            return false;
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
