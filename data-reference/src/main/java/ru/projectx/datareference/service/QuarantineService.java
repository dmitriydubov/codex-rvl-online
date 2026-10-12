package ru.projectx.datareference.service;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.QuarantinedMessage;
import ru.projectx.datareference.entity.RejectedMessage;
import ru.projectx.datareference.repository.QuarantineRepository;

@Service
public class QuarantineService {

    private final QuarantineRepository repository;
    private final Clock clock;

    public QuarantineService(QuarantineRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean quarantine(RejectedMessage rejected, String reason) {
        if (repository.existsById(rejected.messageId())) {
            return false;
        }
        repository.save(QuarantinedMessage.of(rejected, reason, clock.instant()));
        return true;
    }
}
