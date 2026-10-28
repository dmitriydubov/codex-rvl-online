package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.inbox.QuarantinedMessage;
import ru.projectx.contracts.entity.inbox.RejectedMessage;
import ru.projectx.contracts.repository.journal.QuarantineRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;

@Service
public class QuarantineService {

    private final QuarantineRepository quarantine;
    private final LeadershipFence fence;
    private final Clock clock;

    public QuarantineService(QuarantineRepository quarantine, LeadershipFence fence, Clock clock) {
        this.quarantine = quarantine;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public boolean quarantine(RejectedMessage rejected, String reason) {
        boolean quarantined = quarantine.existsById(rejected.messageId());
        fence.holdUntilCommit();
        if (quarantined) {
            return false;
        }
        quarantine.save(QuarantinedMessage.of(rejected, reason, clock.instant()));
        return true;
    }
}
