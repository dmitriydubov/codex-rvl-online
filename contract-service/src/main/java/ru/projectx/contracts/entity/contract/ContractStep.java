package ru.projectx.contracts.entity.contract;

public enum ContractStep {
    OFFER_ACCEPTED(ContractStatus.DRAFT_READY, ContractStatus.ACCEPTED),
    INVOICE_ISSUED(ContractStatus.ACCEPTED, ContractStatus.INVOICE_ISSUED);

    private final ContractStatus from;
    private final ContractStatus to;

    ContractStep(ContractStatus from, ContractStatus to) {
        this.from = from;
        this.to = to;
    }

    ContractStatus from() {
        return from;
    }

    ContractStatus to() {
        return to;
    }
}
