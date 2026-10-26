package ru.projectx.contracts.service.usk;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.outbox.OutboxDrafts;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.status.RequestStatusPublication;

public abstract class UskRequestRecording<R extends IssuanceRecord, S extends IssuanceStatus> {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final OutboxJournal journal;
    private final RequestStatusPublication statuses;
    private final Clock clock;

    protected UskRequestRecording(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records, OutboxJournal journal,
            RequestStatusPublication statuses, Clock clock) {
        this.kind = kind;
        this.records = records;
        this.journal = journal;
        this.statuses = statuses;
        this.clock = clock;
    }

    @Transactional
    public void submit(UUID id, ExternalIdentifiers identifiers) {
        R record = load(id);
        if (!kind.ready(record)) {
            return;
        }
        Instant now = clock.instant();
        record.submit(now);
        UskSubmission submission = compose(record, identifiers, now);
        journal.record(submission.draft(), submission.attachments(), now);
    }

    @Transactional
    public void refuse(UUID id, IssueError error) {
        R record = load(id);
        if (!kind.ready(record)) {
            return;
        }
        RecordErrors.enter(statuses, record, error, clock.instant());
    }

    protected abstract UskSubmission compose(R record, ExternalIdentifiers identifiers, Instant now);

    protected OutboxDraft draft(R record, MessageMeta meta, String payload) {
        return OutboxDrafts.of(kind.aggregateType(), record.id(), meta, payload);
    }

    private R load(UUID id) {
        return records.findLockedById(id)
                .orElseThrow(() -> new IllegalStateException(kind.wording().kindGenitive() + " " + id + " нет"));
    }
}
