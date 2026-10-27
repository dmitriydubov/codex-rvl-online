package ru.projectx.contracts.service.lm;

import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

public record LmRequestFacts(PersonSnapshot insured, ExternalIdentifiers identifiers) {
}
