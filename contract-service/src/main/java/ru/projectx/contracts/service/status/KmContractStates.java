package ru.projectx.contracts.service.status;

import java.util.Optional;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.message.KmContractState;

final class KmContractStates {

    private KmContractStates() {
    }

    static boolean undrafted(IssuanceStatus status) {
        return status == ContractStatus.NEW;
    }

    static boolean anyUndrafted(IssuanceSnapshot snapshot) {
        return snapshot.composition().stream()
                .filter(participant -> participant.kind() == DocumentKind.KM_CONTRACT)
                .map(snapshot::statusOf)
                .flatMap(Optional::stream)
                .anyMatch(KmContractStates::undrafted);
    }

    static KmContractState of(ContractStatus status) {
        return switch (status) {
            case NEW -> throw new IllegalStateException("договор КМ без проекта в ответ сервису Заявок не попадает");
            case DRAFT_READY, ACCEPTED, INVOICE_ISSUED, PAID, SUBMITTED -> KmContractState.DRAFTED;
            case ISSUED -> KmContractState.ISSUED;
            case ERROR, CANCELLED -> KmContractState.CANCELLED;
        };
    }
}
