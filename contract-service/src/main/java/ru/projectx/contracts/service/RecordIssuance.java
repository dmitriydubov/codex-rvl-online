package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.exception.ParticipantDataException;

public abstract class RecordIssuance<R extends IssuanceRecord> {

    private final PartySnapshotStore parties;

    protected RecordIssuance(PartySnapshotStore parties) {
        this.parties = parties;
    }

    public void reconcile(Issue issue, Optional<R> existing, Instant now) {
        List<Party> snapshot;
        try {
            snapshot = parties(issue);
        }
        catch (ParticipantDataException invalid) {
            IssueError error = new IssueError(invalid.code(), invalid.getMessage());
            existing.ifPresentOrElse(record -> record.markError(error, now),
                    () -> open(issue, now).markError(error, now));
            return;
        }
        existing.ifPresentOrElse(record -> revise(record, issue, snapshot, now),
                () -> parties.store(owner(open(issue, now)), snapshot, now));
    }

    protected abstract List<Party> parties(Issue issue);

    protected abstract R open(Issue issue, Instant now);

    protected abstract PersonOwner owner(R record);

    protected abstract void reviseTerms(R record, Issue issue, Instant now);

    private void revise(R record, Issue issue, List<Party> snapshot, Instant now) {
        reviseTerms(record, issue, now);
        if (parties.replaceIfChanged(owner(record), snapshot, now)) {
            record.markPersonsReplaced(now);
        }
    }
}
