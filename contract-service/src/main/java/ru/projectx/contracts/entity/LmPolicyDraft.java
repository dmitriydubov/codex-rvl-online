package ru.projectx.contracts.entity;

import java.util.UUID;

public record LmPolicyDraft(IssuanceTerms terms, UUID participantId) {
}
