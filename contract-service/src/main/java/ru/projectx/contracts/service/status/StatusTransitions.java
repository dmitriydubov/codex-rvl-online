package ru.projectx.contracts.service.status;

import ru.projectx.contracts.message.RequestStatus;

final class StatusTransitions {

    private StatusTransitions() {
    }

    static boolean publishAfterChange(IssuanceSnapshot before, IssuanceSnapshot after) {
        return !KmContractStates.anyUndrafted(after) && (enteredSince(before, after) || completedSince(before, after));
    }

    private static boolean enteredSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.records().stream().filter(StatusTransitions::reportedOnEntry)
                .anyMatch(record -> !before.knows(record));
    }

    private static boolean reportedOnEntry(RecordOutcome record) {
        return record.stopped() || KmContractStates.reportedOnEntry(record.status());
    }

    private static boolean completedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.requestStatus() == RequestStatus.COMPLETED
                && (before.requestStatus() != RequestStatus.COMPLETED
                        || !before.composition().equals(after.composition()));
    }
}
