package ru.projectx.contracts.service.usk;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.repository.UskRecordRepository;

public class UskAwaiting<R extends IssuanceRecord, S extends IssuanceStatus> {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final AwaitingMetrics metrics;
    private final Duration threshold;
    private final Clock clock;

    protected UskAwaiting(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records, AwaitingMetrics metrics,
            Duration threshold, Clock clock) {
        this.kind = kind;
        this.records = records;
        this.metrics = metrics;
        this.threshold = threshold;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void refresh() {
        Instant now = clock.instant();
        Duration oldest = records.findFirstByStatusOrderByUpdatedAtAscIdAsc(kind.awaitingUsk())
                .map(record -> Duration.between(record.state().updatedAt(), now))
                .orElse(Duration.ZERO);
        metrics.show(oldest, records.countByStatusAndUpdatedAtBefore(kind.awaitingUsk(), now.minus(threshold)));
    }
}
