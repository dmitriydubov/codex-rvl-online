package ru.projectx.contracts.entity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record IssuanceState(
        RequestReference request,
        UUID policyholderParticipantId,
        Optional<UUID> programId,
        TripPeriod period,
        Optional<IssueError> error,
        Optional<Cancellation> cancellation,
        int entityVersion,
        Instant updatedAt) {
}
