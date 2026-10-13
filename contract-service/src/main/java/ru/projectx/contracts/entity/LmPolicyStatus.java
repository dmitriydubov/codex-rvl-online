package ru.projectx.contracts.entity;

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
