package ru.projectx.datareference.service.metrics;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.QuarantineRepository;
import ru.projectx.datareference.repository.inbound.StatusBacklog;
import ru.projectx.datareference.service.retention.ExpiredPolicyPersonalData;

@Component
public class InboundBacklogReads {

    private final InboundMessageRepository inboundMessages;
    private final QuarantineRepository quarantine;
    private final ExpiredPolicyPersonalData expiredPolicies;
    private final ImportProperties properties;
    private final Clock clock;

    public InboundBacklogReads(InboundMessageRepository inboundMessages, QuarantineRepository quarantine,
            ExpiredPolicyPersonalData expiredPolicies, ImportProperties properties, Clock clock) {

        this.inboundMessages = inboundMessages;
        this.quarantine = quarantine;
        this.expiredPolicies = expiredPolicies;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InboundBacklog read() {
        Instant now = clock.instant();
        return new InboundBacklog(
                inboundMessages.countUnfinishedByStreamAndStatus().stream().map(InboundBacklogReads::statusCount).toList(),
                inboundMessages.countTransfersBufferedBefore(now.minus(properties.stuckTransferThreshold())),
                new InboundBacklog.Overdue(
                        inboundMessages.countPayloadsToForgetReceivedBefore(now.minus(properties.payloadTtl())),
                        quarantine.countBodiesReceivedBefore(now.minus(properties.quarantineTtl())),
                        expiredPolicies.countAt(now)),
                now);
    }

    private static InboundBacklog.StatusCount statusCount(StatusBacklog row) {
        return new InboundBacklog.StatusCount(row.getStream(), row.getStatus(), row.getMessages(), row.getOldestReceivedAt());
    }
}
