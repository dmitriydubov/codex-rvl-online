package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.message.TaskParticipant;

public enum IssuanceKind {
    NONE(DocumentKind.NONE), KM_CONTRACT(DocumentKind.KM_CONTRACT), LM_POLICY(DocumentKind.LM_POLICY);

    private final DocumentKind document;

    IssuanceKind(DocumentKind document) {
        this.document = document;
    }

    public static IssuanceKind of(TaskParticipant participant) {
        if (participant.pays()) {
            return KM_CONTRACT;
        }
        return participant.relative() ? LM_POLICY : NONE;
    }

    public DocumentKind document() {
        return document;
    }
}
