package ru.projectx.contracts.entity.policy;

import ru.projectx.contracts.entity.issuance.IssuanceOutcome;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;

public enum LmPolicyStatus implements IssuanceStatus {
    NEW(IssuanceOutcome.PENDING),
    SUBMITTED(IssuanceOutcome.PENDING),
    ISSUED(IssuanceOutcome.ISSUED),
    ERROR(IssuanceOutcome.ERROR),
    CANCELLED(IssuanceOutcome.CANCELLED);

    private final IssuanceOutcome outcome;

    LmPolicyStatus(IssuanceOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public IssuanceOutcome outcome() {
        return outcome;
    }
}
