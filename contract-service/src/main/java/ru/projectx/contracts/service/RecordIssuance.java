package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceState;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.exception.ParticipantDataException;

public abstract class RecordIssuance<R extends IssuanceRecord> {

    private static final Logger log = LoggerFactory.getLogger(RecordIssuance.class);

    private final PartySnapshotStore parties;
    private final String frozenBecause;

    protected RecordIssuance(PartySnapshotStore parties, String frozenBecause) {
        this.parties = parties;
        this.frozenBecause = frozenBecause;
    }

    public void reconcile(Issue issue, Optional<R> existing, Instant now) {
        Optional<R> frozen = existing.filter(this::frozen);
        if (frozen.isPresent()) {
            keepFrozen(frozen.get(), issue);
            return;
        }
        IssueData data;
        try {
            data = read(issue);
        }
        catch (ParticipantDataException invalid) {
            IssueError error = new IssueError(invalid.code(), invalid.getMessage());
            existing.ifPresentOrElse(record -> record.markError(error, now),
                    () -> open(issue, now).markError(error, now));
            return;
        }
        existing.ifPresentOrElse(record -> revise(record, issue, data, now),
                () -> parties.store(owner(open(issue, now)), data.snapshot(), now));
    }

    protected abstract String kind();

    protected abstract List<Party> parties(Issue issue);

    protected abstract R open(Issue issue, Instant now);

    protected abstract PersonOwner owner(R record);

    protected abstract boolean frozen(R record);

    protected abstract boolean reviseTerms(R record, Issue issue, UUID program);

    private IssueData read(Issue issue) {
        return new IssueData(parties(issue), issue.program());
    }

    private void revise(R record, Issue issue, IssueData data, Instant now) {
        boolean termsChanged = reviseTerms(record, issue, data.program());
        boolean partiesChanged = parties.replaceIfChanged(owner(record), data.snapshot(), now);
        if (termsChanged || partiesChanged) {
            record.advanceVersion(now);
        }
    }

    private void keepFrozen(R record, Issue issue) {
        if (!changesFor(record, issue)) {
            return;
        }
        log.warn("{} {} участника {} заявки {}: {} {}, изменения из задачи версии {} не применены",
                kind(), record.id(), record.participantId(), issue.task().individualRequestId(), kind(),
                frozenBecause, issue.task().entityVersion());
    }

    private boolean changesFor(R record, Issue issue) {
        try {
            IssueData data = read(issue);
            return !keepsTerms(record, issue, data.program()) || parties.differ(owner(record), data.snapshot());
        }
        catch (ParticipantDataException invalid) {
            return true;
        }
    }

    protected boolean keepsTerms(R record, Issue issue, UUID program) {
        IssuanceState state = record.state();
        return state.period().equals(issue.period()) && state.programId().filter(program::equals).isPresent();
    }

    private record IssueData(List<Party> snapshot, UUID program) {
    }
}
