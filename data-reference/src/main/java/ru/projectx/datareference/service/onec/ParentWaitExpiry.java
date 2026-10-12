package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.ParentWaitRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

@Component
public class ParentWaitExpiry {

    private final ParentWaitRepository waits;
    private final InboundMessageRepository inbound;
    private final InboundTransitionMetrics transitions;
    private final ImportProperties properties;
    private final Clock clock;

    public ParentWaitExpiry(ParentWaitRepository waits, InboundMessageRepository inbound,
            InboundTransitionMetrics transitions, ImportProperties properties, Clock clock) {

        this.waits = waits;
        this.inbound = inbound;
        this.transitions = transitions;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<ExpiredParentWait> expireOverdue() {
        Instant deadline = clock.instant().minus(properties.parentWaitTimeout());
        List<InboundMessage> overdue = waits.findExpiredUnresolved(deadline, Limit.of(properties.batchSize()))
                .stream()
                .flatMap(this::withTransferParts)
                .distinct()
                .toList();
        for (InboundMessage message : overdue) {
            message.markParentWaitExpired();
            transitions.record(message);
        }
        return overdue.stream().map(ExpiredParentWait::of).toList();
    }

    private Stream<InboundMessage> withTransferParts(InboundMessage message) {
        return Optional.ofNullable(message.transferPart().correlationId())
                .map(inbound::findByCorrelationId)
                .orElseGet(() -> List.of(message))
                .stream()
                .filter(part -> part.outcome().status() == InboundStatus.AWAITING_PARENT);
    }
}
