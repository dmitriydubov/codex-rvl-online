package ru.projectx.contracts.exception;

import java.util.UUID;

public class ContractNotFoundException extends AcceptanceRefusedException {

    private static final long serialVersionUID = 1L;

    public ContractNotFoundException(UUID contractId) {
        super(AcceptanceRefusal.CONTRACT_NOT_FOUND, "договора КМ " + contractId + " нет");
    }
}
