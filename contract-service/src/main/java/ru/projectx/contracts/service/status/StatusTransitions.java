package ru.projectx.contracts.service.status;

import ru.projectx.contracts.message.RequestStatus;

final class StatusTransitions {

    private StatusTransitions() {
    }

    static boolean publishAfterChange(IssuanceSnapshot before, IssuanceSnapshot after) {
        return !KmContractStates.anyUndrafted(after) && (stoppedSince(before, after) || completedSince(before, after));
    }

    private static boolean stoppedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.records().stream().filter(RecordOutcome::stopped).anyMatch(record -> !before.knows(record));
    }

    private static boolean completedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.requestStatus() == RequestStatus.COMPLETED
                && (before.requestStatus() != RequestStatus.COMPLETED
                        || !before.composition().equals(after.composition()));
    }
}
