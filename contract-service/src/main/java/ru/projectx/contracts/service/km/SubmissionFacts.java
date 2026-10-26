package ru.projectx.contracts.service.km;

import java.time.Instant;
import java.util.List;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.service.outbox.OutboundAttachment;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

public record SubmissionFacts(
        ExternalIdentifiers identifiers,
        Instant acceptedAt,
        PersonSnapshot policyholder,
        PersonSnapshot insured,
        List<OutboundAttachment> certificates) {
}
