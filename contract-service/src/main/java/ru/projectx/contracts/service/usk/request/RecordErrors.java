package ru.projectx.contracts.service.usk.request;

import java.time.Instant;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

public final class RecordErrors {

    private RecordErrors() {
    }

    public static void enter(RequestStatusPublication statuses, IssuanceRecord record, IssueError error, Instant now) {
        IssuanceSnapshot before = statuses.watch(record.state().request().individualRequestId());
        record.markError(error, now);
        statuses.afterRecordChange(before, now);
    }
}
