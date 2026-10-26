package ru.projectx.contracts.service.reference;

import java.util.UUID;

public record ExternalIdentifiers(UUID programExternalId, UUID insuranceContractExternalId, UUID insurerPolicyExternalId) {
}
