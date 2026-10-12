package ru.projectx.contracts.entity;

import java.util.UUID;

public record LmPolicyDraft(
        RequestReference request,
        UUID participantId,
        UUID policyholderParticipantId,
        TripPeriod period) {
}
