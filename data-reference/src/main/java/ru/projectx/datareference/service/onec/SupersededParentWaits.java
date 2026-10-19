package ru.projectx.datareference.service.onec;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.repository.inbound.ParentWaitRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

@Component
class SupersededParentWaits {

    private final ParentWaitRepository waits;
    private final InboundTransitionMetrics transitions;
    private final Clock clock;

    SupersededParentWaits(ParentWaitRepository waits, InboundTransitionMetrics transitions, Clock clock) {
        this.waits = waits;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Transactional
    void closeOlderThan(InboundReceipt newer, UUID record, String newerOutcome) {
        String reason = "по записи %s уже %s более новое сообщение %s"
                .formatted(record, newerOutcome, newer.messageId());
        for (InboundMessage older : waits.findAwaitingOfRecordBefore(newer.stream(), record, newer.receivedAt())) {
            older.markSkipped(reason, clock.instant());
            transitions.record(older);
        }
    }
}
