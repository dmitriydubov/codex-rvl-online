package ru.projectx.datareference.service.inbound;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.inbound.QuarantinedMessage;
import ru.projectx.datareference.entity.inbound.RejectedMessage;
import ru.projectx.datareference.repository.database.DatabaseClockRepository;
import ru.projectx.datareference.repository.inbound.QuarantineRepository;

@Service
public class QuarantineService {

    private final QuarantineRepository repository;
    private final DatabaseClockRepository databaseClock;

    public QuarantineService(QuarantineRepository repository, DatabaseClockRepository databaseClock) {
        this.repository = repository;
        this.databaseClock = databaseClock;
    }

    @Transactional
    public boolean quarantine(RejectedMessage rejected, String reason) {
        if (repository.existsById(rejected.messageId())) {
            return false;
        }
        repository.save(QuarantinedMessage.of(rejected, reason, databaseClock.currentMoment()));
        return true;
    }
}
