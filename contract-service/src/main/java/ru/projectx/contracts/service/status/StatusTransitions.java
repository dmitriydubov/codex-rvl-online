package ru.projectx.contracts.service.status;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.status.RequestStatus;

final class StatusTransitions {

    private StatusTransitions() {
    }

    static boolean publishAfterChange(IssuanceSnapshot before, IssuanceSnapshot after) {
        return !KmContractStates.anyUndrafted(after)
                && (participantChangedSince(before, after) || completedSince(before, after));
    }

    private static boolean participantChangedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return enteredSince(before, after) || leftErrorSince(before, after);
    }

    private static boolean enteredSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.records().stream().filter(StatusTransitions::reportedOnEntry)
                .anyMatch(record -> !before.knows(record));
    }

    private static boolean reportedOnEntry(RecordOutcome record) {
        return record.stopped() || KmContractStates.reportedOnEntry(record.status());
    }

    private static boolean leftErrorSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return !failedParticipants(after).containsAll(failedParticipants(before));
    }

    private static Set<UUID> failedParticipants(IssuanceSnapshot snapshot) {
        return snapshot.composition().stream()
                .filter(participant -> snapshot.statusOf(participant).filter(IssuanceStatus::error).isPresent())
                .map(RequestParticipant::participantId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean completedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.requestStatus() == RequestStatus.COMPLETED
                && (before.requestStatus() != RequestStatus.COMPLETED
                        || !before.composition().equals(after.composition()));
    }
}
