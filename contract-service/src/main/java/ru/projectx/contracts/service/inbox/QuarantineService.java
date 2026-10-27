package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.QuarantinedMessage;
import ru.projectx.contracts.entity.RejectedMessage;
import ru.projectx.contracts.repository.QuarantineRepository;

@Service
public class QuarantineService {

    private final QuarantineRepository quarantine;
    private final Clock clock;

    public QuarantineService(QuarantineRepository quarantine, Clock clock) {
        this.quarantine = quarantine;
        this.clock = clock;
    }

    @Transactional
    public boolean quarantine(RejectedMessage rejected, String reason) {
        if (quarantine.existsById(rejected.messageId())) {
            return false;
        }
        quarantine.save(QuarantinedMessage.of(rejected, reason, clock.instant()));
        return true;
    }
}
