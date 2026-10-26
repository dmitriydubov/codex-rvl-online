package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;

final class PaidContracts {

    private PaidContracts() {
    }

    static boolean failedAfterPayment(Contract contract) {
        return contract.status() == ContractStatus.ERROR && contract.progress().paidAt().isPresent();
    }
}
