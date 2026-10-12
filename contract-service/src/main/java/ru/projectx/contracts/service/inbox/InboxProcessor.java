package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.entity.InboxMessage;
import ru.projectx.contracts.repository.InboxMessageRepository;

@Service
public class InboxProcessor {

    private final InboxMessageRepository messages;
    private final InboxMessageHandling handling;
    private final InboxOutcomes outcomes;
    private final InboxProperties properties;
    private final Clock clock;

    public InboxProcessor(InboxMessageRepository messages, InboxMessageHandling handling, InboxOutcomes outcomes,
            InboxProperties properties, Clock clock) {
        this.messages = messages;
        this.handling = handling;
        this.outcomes = outcomes;
        this.properties = properties;
        this.clock = clock;
    }

    public int processDue() {
        List<UUID> due = messages.findDue(clock.instant(), Limit.of(properties.processing().batchSize())).stream()
                .map(InboxMessage::id)
                .toList();
        for (UUID id : due) {
            processOne(id);
        }
        return due.size();
    }

    private void processOne(UUID id) {
        try {
            handling.process(id);
        }
        catch (RuntimeException failure) {
            outcomes.recordFailure(id, InboxFailure.of(failure));
        }
    }
}
