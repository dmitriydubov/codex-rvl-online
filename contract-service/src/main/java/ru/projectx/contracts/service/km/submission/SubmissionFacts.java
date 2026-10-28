package ru.projectx.contracts.service.km.submission;

import java.time.Instant;
import java.util.List;
import ru.projectx.contracts.entity.party.PersonSnapshot;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

public record SubmissionFacts(
        ExternalIdentifiers identifiers,
        Instant acceptedAt,
        PersonSnapshot policyholder,
        PersonSnapshot insured,
        List<SubmittedCertificate> certificates) {
}
