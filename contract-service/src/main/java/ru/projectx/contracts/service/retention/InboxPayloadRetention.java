package ru.projectx.contracts.service.retention;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Component
public class InboxPayloadRetention extends PayloadRetention {

    public InboxPayloadRetention(InboxMessageRepository messages, InboxProperties properties,
            TransactionTemplate transaction, Clock clock, BackgroundLeadership leadership) {
        super(properties.retention().payload(), properties.retention().batchSize(), transaction, clock, leadership,
                messages::erasePayloadsReceivedBefore);
    }
}
