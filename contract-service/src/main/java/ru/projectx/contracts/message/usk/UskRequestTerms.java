package ru.projectx.contracts.message.usk;

import java.time.LocalDate;
import java.util.UUID;

public record UskRequestTerms(
        UUID individualRequestId,
        UUID insuranceContractExternalId,
        UUID insurerPolicyExternalId,
        UUID programExternalId,
        LocalDate dateFrom,
        LocalDate dateTo) {
}
