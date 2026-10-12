package ru.projectx.contracts.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record ContractDraft(
        String number,
        RequestReference request,
        UUID insuredParticipantId,
        UUID policyholderParticipantId,
        TripPeriod period,
        BigDecimal premium) {
}
