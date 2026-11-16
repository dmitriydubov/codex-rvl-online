package ru.projectx.contracts.service.usk;

import java.util.UUID;
import java.util.function.Function;
import ru.projectx.contracts.entity.issuance.IssuanceFlow;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.service.IssuanceErrors;

public record IssuanceKind<R extends IssuanceRecord, S extends IssuanceStatus>(
        UskWording wording,
        String aggregateType,
        String requestEventType,
        String resultEventType,
        IssuanceFlow<S> flow,
        IssuanceErrors errors,
        Function<UUID, PersonOwner> owner,
        IssuanceCompletion<R> completion) {

    public S readyForUsk() {
        return flow.submittedFrom();
    }

    public S awaitingUsk() {
        return flow.submitted();
    }

    public boolean ready(R record) {
        return readyForUsk().equals(record.status());
    }

    public boolean awaiting(R record) {
        return awaitingUsk().equals(record.status());
    }

    public boolean requested(R record) {
        return awaiting(record) || record.finished();
    }
}
