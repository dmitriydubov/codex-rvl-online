package ru.projectx.contracts.message;

import java.time.LocalDate;
import java.util.UUID;

public record LmPolicyRequest(
        UUID id,
        UUID individualRequestId,
        UUID insuranceContractExternalId,
        UUID insurerPolicyExternalId,
        UUID programExternalId,
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID policyholderParticipantId,
        InsuredPerson insured) {
}
