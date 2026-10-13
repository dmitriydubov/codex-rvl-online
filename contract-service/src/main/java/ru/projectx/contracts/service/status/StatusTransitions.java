package ru.projectx.contracts.service.status;

import java.util.Map;
import java.util.UUID;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.message.RequestStatus;

final class StatusTransitions {

    private StatusTransitions() {
    }

    static boolean publishAfterTask(IssuanceSnapshot before, IssuanceSnapshot after) {
        return !after.hasUndraftedKmContract() && (stoppedSince(before, after) || completedSince(before, after));
    }

    private static boolean stoppedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        Map<UUID, IssuanceStatus> earlier = before.recordStatuses();
        return after.recordStatuses().entrySet().stream()
                .filter(record -> record.getValue().stopped())
                .anyMatch(record -> !record.getValue().equals(earlier.get(record.getKey())));
    }

    private static boolean completedSince(IssuanceSnapshot before, IssuanceSnapshot after) {
        return after.requestStatus() == RequestStatus.COMPLETED
                && (before.requestStatus() != RequestStatus.COMPLETED
                        || !before.composition().equals(after.composition()));
    }
}
