package ru.projectx.contracts.entity.policy;

import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;

public record LmPolicyDraft(IssuanceTerms terms, UUID participantId) {
}
