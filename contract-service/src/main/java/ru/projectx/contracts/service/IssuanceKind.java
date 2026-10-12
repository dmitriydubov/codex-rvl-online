package ru.projectx.contracts.service;

import ru.projectx.contracts.message.TaskParticipant;

public enum IssuanceKind {
    NONE, KM_CONTRACT, LM_POLICY;

    public static IssuanceKind of(TaskParticipant participant) {
        if (participant.pays()) {
            return KM_CONTRACT;
        }
        return participant.relative() ? LM_POLICY : NONE;
    }
}
