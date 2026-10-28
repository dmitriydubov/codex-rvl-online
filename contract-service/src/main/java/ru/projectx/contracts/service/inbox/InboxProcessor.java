package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

@Service
public class InboxProcessor {

    private final InboxMessageRepository messages;
    private final InboxMessageHandling handling;
    private final InboxOutcomes outcomes;
    private final InboxProperties properties;
    private final BackgroundLeadership leadership;
    private final Clock clock;

    public InboxProcessor(InboxMessageRepository messages, InboxMessageHandling handling, InboxOutcomes outcomes,
            InboxProperties properties, BackgroundLeadership leadership, Clock clock) {
        this.messages = messages;
        this.handling = handling;
        this.outcomes = outcomes;
        this.properties = properties;
        this.leadership = leadership;
        this.clock = clock;
    }

    public int processDue() {
        return leadership.countAsLeader(() -> process(dueMessageIds()));
    }

    private List<UUID> dueMessageIds() {
        return messages.findDue(clock.instant(), Limit.of(properties.processing().batchSize())).stream()
                .map(InboxMessage::id)
                .toList();
    }

    private int process(List<UUID> due) {
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
            recordUnlessLeadershipLost(id, failure);
        }
    }

    private void recordUnlessLeadershipLost(UUID id, RuntimeException failure) {
        if (failure instanceof LeadershipLostException lost) {
            throw lost;
        }
        outcomes.recordFailure(id, InboxFailure.of(failure));
    }
}
