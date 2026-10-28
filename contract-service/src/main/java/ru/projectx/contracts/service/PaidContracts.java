package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;

final class PaidContracts {

    private PaidContracts() {
    }

    static boolean failedAfterPayment(Contract contract) {
        return contract.status() == ContractStatus.ERROR && contract.progress().paidAt().isPresent();
    }
}
