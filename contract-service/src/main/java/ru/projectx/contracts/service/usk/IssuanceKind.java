package ru.projectx.contracts.service.usk;

import java.util.UUID;
import java.util.function.Function;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.PersonOwner;

public record IssuanceKind<R extends IssuanceRecord, S extends IssuanceStatus>(
        UskWording wording,
        String aggregateType,
        String resultEventType,
        S readyForUsk,
        S awaitingUsk,
        IssueError undelivered,
        Function<UUID, PersonOwner> owner,
        IssuanceCompletion<R> completion) {

    public boolean ready(R record) {
        return readyForUsk.equals(record.status());
    }

    public boolean awaiting(R record) {
        return awaitingUsk.equals(record.status());
    }

    public boolean requested(R record) {
        return awaiting(record) || record.finished();
    }
}
