package ru.projectx.contracts.entity;

public enum ContractStatus implements IssuanceStatus {
    NEW(IssuanceOutcome.PENDING),
    DRAFT_READY(IssuanceOutcome.PENDING),
    ACCEPTED(IssuanceOutcome.PENDING),
    INVOICE_ISSUED(IssuanceOutcome.PENDING),
    PAID(IssuanceOutcome.PENDING),
    SUBMITTED(IssuanceOutcome.PENDING),
    ISSUED(IssuanceOutcome.ISSUED),
    ERROR(IssuanceOutcome.ERROR),
    CANCELLED(IssuanceOutcome.CANCELLED);

    private final IssuanceOutcome outcome;

    ContractStatus(IssuanceOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public IssuanceOutcome outcome() {
        return outcome;
    }
}
