package ru.projectx.datareference.service.inbound;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.inbound.InboxEntry;
import ru.projectx.datareference.repository.database.DatabaseClockRepository;
import ru.projectx.datareference.repository.inbound.InboxRepository;

@Service
public class InboxService {

    private final InboxRepository repository;
    private final DatabaseClockRepository databaseClock;

    public InboxService(InboxRepository repository, DatabaseClockRepository databaseClock) {
        this.repository = repository;
        this.databaseClock = databaseClock;
    }

    @Transactional
    public boolean registerIfNew(UUID messageId) {
        if (repository.existsById(messageId)) {
            return false;
        }
        repository.save(InboxEntry.receive(messageId, databaseClock.currentMoment()));
        return true;
    }
}
