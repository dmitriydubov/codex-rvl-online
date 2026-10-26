package ru.projectx.contracts.service.usk;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.outbox.RejectedOutboxMessages;
import ru.projectx.contracts.service.status.RequestStatusPublication;

public class UndeliveredUskRequests<R extends IssuanceRecord, S extends IssuanceStatus>
        implements RejectedOutboxMessages {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final RequestStatusPublication statuses;
    private final Logger log;

    protected UndeliveredUskRequests(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records,
            RequestStatusPublication statuses, Logger log) {
        this.kind = kind;
        this.records = records;
        this.statuses = statuses;
        this.log = log;
    }

    @Override
    public String aggregateType() {
        return kind.aggregateType();
    }

    @Override
    public void rejected(UUID id, Instant now) {
        UskWording wording = kind.wording();
        R record = records.findLockedById(id)
                .orElseThrow(() -> new IllegalStateException(wording.kindGenitive() + " " + id + " нет"));
        if (!kind.awaiting(record)) {
            log.warn(wording.undeliveredUnchanged(), id, record.status());
            return;
        }
        RecordErrors.enter(statuses, record, kind.errors().undelivered(), now);
        log.warn(wording.undeliveredErrored(), id);
    }
}
