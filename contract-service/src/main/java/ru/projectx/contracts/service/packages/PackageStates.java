package ru.projectx.contracts.service.packages;

import java.util.Map;
import java.util.Optional;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.LmPolicyStatus;

final class PackageStates {

    private static final Map<IssuanceStatus, DocumentState> RENAMED = Map.of(
            ContractStatus.NEW, DocumentState.NOT_REQUIRED, ContractStatus.DRAFT_READY, DocumentState.DRAFTED,
            ContractStatus.SUBMITTED, DocumentState.PAID, LmPolicyStatus.NEW, DocumentState.NOT_REQUIRED,
            LmPolicyStatus.SUBMITTED, DocumentState.NOT_REQUIRED);

    private PackageStates() {
    }

    static DocumentState of(IssuanceStatus status) {
        return Optional.ofNullable(RENAMED.get(status)).orElseGet(() -> DocumentState.valueOf(status.toString()));
    }
}
