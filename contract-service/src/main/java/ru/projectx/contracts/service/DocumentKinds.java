package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.message.TaskParticipant;

public final class DocumentKinds {

    private DocumentKinds() {
    }

    public static DocumentKind of(TaskParticipant participant) {
        if (participant.pays()) {
            return DocumentKind.KM_CONTRACT;
        }
        return participant.relative() ? DocumentKind.LM_POLICY : DocumentKind.NONE;
    }
}
