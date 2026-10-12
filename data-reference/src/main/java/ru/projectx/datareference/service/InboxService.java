package ru.projectx.datareference.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboxEntry;
import ru.projectx.datareference.repository.InboxRepository;

@Service
public class InboxService {

    private final InboxRepository repository;
    private final Clock clock;

    public InboxService(InboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean registerIfNew(UUID messageId) {
        if (repository.existsById(messageId)) {
            return false;
        }
        repository.save(InboxEntry.receive(messageId, clock.instant()));
        return true;
    }
}
