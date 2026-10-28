package ru.projectx.contracts.entity;

import java.math.BigDecimal;
import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;

public record ContractDraft(String number, IssuanceTerms terms, UUID insuredParticipantId, BigDecimal premium) {
}
