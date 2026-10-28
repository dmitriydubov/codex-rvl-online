package ru.projectx.contracts.service.retention;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.repository.journal.QuarantineRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Component
public class QuarantinePayloadRetention extends PayloadRetention {

    public QuarantinePayloadRetention(QuarantineRepository quarantine, InboxProperties properties,
            TransactionTemplate transaction, Clock clock, BackgroundLeadership leadership) {
        super(properties.retention().payload(), properties.retention().batchSize(), transaction, clock, leadership,
                quarantine::eraseBodiesReceivedBefore);
    }
}
