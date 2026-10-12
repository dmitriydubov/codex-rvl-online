package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.repository.InboxMessageRepository;

@Service
public class InboxPayloadErasure {

    private final InboxMessageRepository messages;
    private final InboxProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public InboxPayloadErasure(InboxMessageRepository messages, InboxProperties properties,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.messages = messages;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public int eraseExpired() {
        Instant cutoff = clock.instant().minus(properties.retention().payload());
        int batch = properties.retention().batchSize();
        int erased = 0;
        int lastBatch = batch;
        while (lastBatch == batch) {
            lastBatch = transaction.execute(status -> messages.erasePayloadsReceivedBefore(cutoff, batch));
            erased += lastBatch;
        }
        return erased;
    }
}
