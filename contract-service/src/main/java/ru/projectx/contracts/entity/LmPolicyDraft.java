package ru.projectx.contracts.entity;

import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;

public record LmPolicyDraft(IssuanceTerms terms, UUID participantId) {
}
