package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ReferenceKind;

public sealed interface ReferenceOutcome {

    record Found(UUID externalId) implements ReferenceOutcome {
    }

    record Refused(IssueError error) implements ReferenceOutcome {
    }

    record Unavailable(ReferenceKind kind) implements ReferenceOutcome {
    }
}
