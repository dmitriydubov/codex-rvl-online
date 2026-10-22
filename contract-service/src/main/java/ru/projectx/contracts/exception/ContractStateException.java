package ru.projectx.contracts.exception;

import java.util.UUID;

public class ContractStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContractStateException(UUID contractId, String status) {
        super("договор КМ " + contractId + " в статусе " + status + " не принимает акцепт");
    }
}
