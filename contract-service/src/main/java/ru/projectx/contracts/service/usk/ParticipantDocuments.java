package ru.projectx.contracts.service.usk;

import ru.projectx.contracts.entity.IdentityDocument;
import ru.projectx.contracts.message.ParticipantDocument;

public final class ParticipantDocuments {

    private ParticipantDocuments() {
    }

    public static ParticipantDocument of(IdentityDocument document) {
        return new ParticipantDocument(document.type().name(), document.series(), document.number(),
                document.issuedBy(), document.issuedAt(), document.divisionCode());
    }
}
