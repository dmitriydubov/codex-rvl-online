package ru.projectx.contracts.entity;

import java.util.Optional;
import java.util.UUID;

public record IssuanceState(
        RequestReference request,
        UUID policyholderParticipantId,
        TripPeriod period,
        Optional<IssueError> error,
        Optional<Cancellation> cancellation,
        int entityVersion) {
}
