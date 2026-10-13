package ru.projectx.contracts.service.lm;

import java.util.UUID;

public record ExternalIdentifiers(UUID programExternalId, UUID insuranceContractExternalId, UUID insurerPolicyExternalId) {
}
