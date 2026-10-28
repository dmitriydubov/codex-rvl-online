package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Optional;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.message.RequestStatus;

final class RequestStatusRules {

    private RequestStatusRules() {
    }

    static RequestStatus of(IssuanceSnapshot snapshot) {
        if (snapshot.cancelled()) {
            return RequestStatus.CANCELLED;
        }
        return ofParticipants(snapshot.composition().stream().map(snapshot::statusOf).toList());
    }

    private static RequestStatus ofParticipants(List<Optional<IssuanceStatus>> statuses) {
        if (statuses.stream().flatMap(Optional::stream).anyMatch(IssuanceStatus::error)) {
            return RequestStatus.ERROR;
        }
        return statuses.stream().allMatch(RequestStatusRules::done) ? RequestStatus.COMPLETED : RequestStatus.IN_PROGRESS;
    }

    private static boolean done(Optional<IssuanceStatus> status) {
        return status.map(IssuanceStatus::issued).orElse(true);
    }
}
