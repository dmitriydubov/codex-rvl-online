package ru.projectx.contracts.service.status;

import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.message.KmContractState;

final class KmContractStates {

    private KmContractStates() {
    }

    static boolean undrafted(ContractStatus status) {
        return status == ContractStatus.NEW;
    }

    static KmContractState of(ContractStatus status) {
        return switch (status) {
            case NEW -> throw new IllegalStateException("договор КМ без проекта в ответ сервису Заявок не попадает");
            case ERROR, CANCELLED -> KmContractState.CANCELLED;
            case ISSUED -> KmContractState.ISSUED;
            default -> KmContractState.DRAFTED;
        };
    }
}
