package ru.projectx.contracts.service.retention;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.config.journal.OutboxProperties;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Component
public class OutboxPayloadRetention extends PayloadRetention {

    public OutboxPayloadRetention(OutboxMessageRepository messages, OutboxProperties properties,
            TransactionTemplate transaction, Clock clock, BackgroundLeadership leadership) {
        super(properties.retention().payload(), properties.retention().batchSize(), transaction, clock, leadership,
                messages::eraseFinishedPayloadsCreatedBefore);
    }
}
