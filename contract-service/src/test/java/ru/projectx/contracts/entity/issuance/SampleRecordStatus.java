package ru.projectx.contracts.entity.issuance;

enum SampleRecordStatus implements IssuanceStatus {
    READY(IssuanceOutcome.PENDING),
    SENT(IssuanceOutcome.PENDING),
    ISSUED(IssuanceOutcome.ISSUED),
    FAILED(IssuanceOutcome.ERROR),
    STOPPED(IssuanceOutcome.CANCELLED);

    private final IssuanceOutcome outcome;

    SampleRecordStatus(IssuanceOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public IssuanceOutcome outcome() {
        return outcome;
    }
}
