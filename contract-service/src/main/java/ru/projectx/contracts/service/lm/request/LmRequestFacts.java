package ru.projectx.contracts.service.lm.request;

import ru.projectx.contracts.entity.party.PersonSnapshot;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

public record LmRequestFacts(PersonSnapshot insured, ExternalIdentifiers identifiers) {
}
