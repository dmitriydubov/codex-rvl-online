package ru.projectx.contracts.service.status;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.IssueError;

record RecordOutcome(UUID recordId, UUID participantId, IssuanceStatus status, @Nullable IssueError error) {

    static RecordOutcome of(IssuanceRecord record) {
        return new RecordOutcome(record.id(), record.participantId(), record.status(),
                record.state().error().orElse(null));
    }

    boolean stopped() {
        return status.stopped();
    }

    boolean looksLike(@Nullable RecordOutcome earlier) {
        return earlier != null && status.equals(earlier.status) && Objects.equals(error, earlier.error);
    }
}
