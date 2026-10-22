package ru.projectx.contracts.exception;

import java.util.UUID;

public class ContractBusyException extends AcceptanceRefusedException {

    private static final long serialVersionUID = 1L;

    public ContractBusyException(UUID contractId, Throwable cause) {
        super(AcceptanceRefusal.CONTRACT_BUSY, "договор КМ " + contractId + " занят другой операцией", cause);
    }
}
