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

    protected RecordIssuance(PartySnapshotStore parties) {
        this.parties = parties;
    }

    public void reconcile(Issue issue, Optional<R> existing, Instant now) {
        Optional<R> submitted = existing.filter(this::submitted);
        if (submitted.isPresent()) {
            keepSubmitted(submitted.get(), issue);
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

    protected abstract boolean submitted(R record);

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

    private void keepSubmitted(R record, Issue issue) {
        if (!changesFor(record, issue)) {
            return;
        }
        log.warn("{} {} участника {} заявки {}: {} передан в 1С:УСК, изменения из задачи версии {} не применены",
                kind(), record.id(), record.participantId(), issue.task().individualRequestId(), kind(),
                issue.task().entityVersion());
    }

    private boolean changesFor(R record, Issue issue) {
        try {
            IssueData data = read(issue);
            return !sameTerms(record.state(), issue, data) || parties.differ(owner(record), data.snapshot());
        }
        catch (ParticipantDataException invalid) {
            return true;
        }
    }

    private static boolean sameTerms(IssuanceState state, Issue issue, IssueData data) {
        return state.period().equals(issue.period()) && state.programId().filter(data.program()::equals).isPresent();
    }

    private record IssueData(List<Party> snapshot, UUID program) {
    }
}
