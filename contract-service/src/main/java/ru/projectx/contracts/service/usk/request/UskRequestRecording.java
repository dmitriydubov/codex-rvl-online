package ru.projectx.contracts.service.usk.request;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.outbox.OutboxDrafts;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.IssuanceKind;

public class UskRequestRecording<R extends IssuanceRecord, S extends IssuanceStatus, F> {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final UskRequestFacts<R, F> facts;
    private final UskRequestMessages<R, F> messages;
    private final OutboxJournal journal;
    private final RequestStatusPublication statuses;
    private final LeadershipFence fence;
    private final Clock clock;

    protected UskRequestRecording(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records,
            UskRequestFacts<R, F> facts, UskRequestMessages<R, F> messages, OutboxJournal journal,
            RequestStatusPublication statuses, LeadershipFence fence, Clock clock) {
        this.kind = kind;
        this.records = records;
        this.facts = facts;
        this.messages = messages;
        this.journal = journal;
        this.statuses = statuses;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public void submit(UUID id, ExternalIdentifiers identifiers) {
        R record = load(id);
        if (!kind.ready(record)) {
            return;
        }
        fence.holdUntilCommit();
        Instant now = clock.instant();
        record.submit(now);
        journal.record(compose(record, identifiers, now), now);
    }

    @Transactional
    public boolean refuse(UUID id, IssueError error) {
        R record = load(id);
        if (!kind.ready(record)) {
            return false;
        }
        fence.holdUntilCommit();
        RecordErrors.enter(statuses, record, error, clock.instant());
        return true;
    }

    private OutboxDraft compose(R record, ExternalIdentifiers identifiers, Instant now) {
        MessageMeta meta = messages.meta(record, now);
        String payload = messages.write(meta, record, facts.gather(record, identifiers));
        return OutboxDrafts.of(kind.aggregateType(), record.id(), meta, payload);
    }

    private R load(UUID id) {
        return records.findLockedById(id)
                .orElseThrow(() -> new IllegalStateException(kind.wording().kindGenitive() + " " + id + " нет"));
    }
}
