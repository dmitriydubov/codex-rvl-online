package ru.projectx.contracts.service.usk;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

public class UskAwaiting<R extends IssuanceRecord, S extends IssuanceStatus> {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final AwaitingMetrics metrics;
    private final Duration threshold;
    private final Clock clock;
    private final BackgroundLeadership leadership;
    private final TransactionOperations readOnly;

    protected UskAwaiting(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records, AwaitingMetrics metrics,
            Duration threshold, Clock clock, BackgroundLeadership leadership, PlatformTransactionManager transactions) {
        this.kind = kind;
        this.records = records;
        this.metrics = metrics;
        this.threshold = threshold;
        this.clock = clock;
        this.leadership = leadership;
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setReadOnly(true);
        this.readOnly = template;
    }

    public void refresh() {
        leadership.runByRole(() -> readOnly.executeWithoutResult(status -> show()), metrics::withdraw);
    }

    private void show() {
        Instant now = clock.instant();
        Duration oldest = records.findFirstByStatusOrderByUpdatedAtAscIdAsc(kind.awaitingUsk())
                .map(record -> Duration.between(record.state().updatedAt(), now))
                .orElse(Duration.ZERO);
        metrics.show(oldest, records.countByStatusAndUpdatedAtBefore(kind.awaitingUsk(), now.minus(threshold)));
    }
}
