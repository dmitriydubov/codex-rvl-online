package ru.projectx.contracts.service.lm;

import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ReferenceKind;

public sealed interface RequestResolution {

    record Resolved(ExternalIdentifiers identifiers) implements RequestResolution {
    }

    record Refused(IssueError error) implements RequestResolution {
    }

    record Unavailable(ReferenceKind kind) implements RequestResolution {
    }
}
