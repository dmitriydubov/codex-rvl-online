package ru.projectx.contracts.entity;

import java.math.BigDecimal;
import java.util.UUID;

public record ContractDraft(String number, IssuanceTerms terms, UUID insuredParticipantId, BigDecimal premium) {
}
