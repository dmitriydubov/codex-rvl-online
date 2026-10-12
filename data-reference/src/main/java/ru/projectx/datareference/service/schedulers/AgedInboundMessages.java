package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.QuarantineRepository;

@Component
public class AgedInboundMessages {

    private final InboundMessageRepository repository;
    private final QuarantineRepository quarantine;
    private final Clock clock;

    public AgedInboundMessages(InboundMessageRepository repository, QuarantineRepository quarantine, Clock clock) {
        this.repository = repository;
        this.quarantine = quarantine;
        this.clock = clock;
    }

    public int forgetPayloads(Duration age, int limit) {
        return repository.forgetPayloadsReceivedBefore(receivedBefore(age), limit);
    }

    public int forgetQuarantineBodies(Duration age, int limit) {
        return quarantine.forgetBodies(receivedBefore(age), limit);
    }

    public List<BufferedTransfer> transfersBufferedLongerThan(Duration age, int limit) {
        return repository.findTransfersBufferedBefore(receivedBefore(age), Limit.of(limit));
    }

    public List<BufferedTransfer> allTransfersBufferedLongerThan(Duration age) {
        return repository.findTransfersBufferedBefore(receivedBefore(age), Limit.unlimited());
    }

    private Instant receivedBefore(Duration age) {
        return clock.instant().minus(age);
    }
}
