package ru.projectx.datareference.exception;

import java.util.UUID;

public record ContractPolicy(UUID policyId, UUID contractExternalId) {
}
