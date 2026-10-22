package ru.projectx.contracts.exception;

import java.util.UUID;

public class ContractNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContractNotFoundException(UUID contractId) {
        super("договора КМ " + contractId + " нет");
    }
}
