package ru.projectx.contracts.entity.issuance;

import java.util.UUID;

public record IssuanceFlow<S>(S submittedFrom, S submitted, S failed, S cancelled, String recordName) {

    public S move(S current, S expected, S next, UUID recordId) {
        if (!expected.equals(current)) {
            throw new IllegalStateException(recordName + " " + recordId + " в статусе " + current + ", а не " + expected);
        }
        return next;
    }
}
