package ru.projectx.contracts.entity;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record IssuanceTerms(
        RequestReference request,
        UUID policyholderParticipantId,
        @Nullable UUID programId,
        TripPeriod period) {
}
