package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.Gender;
import ru.projectx.contracts.entity.IdentityDocument;
import ru.projectx.contracts.entity.IdentityDocumentType;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.exception.ParticipantDataException;
import ru.projectx.contracts.message.ParticipantDocument;
import ru.projectx.contracts.message.ParticipantPerson;
import ru.projectx.contracts.message.TaskParticipant;

public final class PersonSnapshots {

    static final String RELATION_TYPE_REQUIRED = "RELATION_TYPE_REQUIRED";
    static final String UNKNOWN_DOCUMENT_TYPE = "UNKNOWN_DOCUMENT_TYPE";
    static final String UNKNOWN_GENDER = "UNKNOWN_GENDER";

    private PersonSnapshots() {
    }

    public static PersonSnapshot of(TaskParticipant participant) {
        ParticipantPerson person = participant.person();
        String path = participant.path() + ".personal_data";
        if (participant.relative() && person.relationType() == null) {
            throw new ParticipantDataException(RELATION_TYPE_REQUIRED,
                    "поле " + path + ".relation_type обязательно для родственника");
        }
        Gender gender = Enums.named(Gender.class, person.gender())
                .orElseThrow(() -> new ParticipantDataException(UNKNOWN_GENDER, invalid(path + ".gender")));
        return new PersonSnapshot(participant.id(), person.lastName(), person.firstName(), person.middleName(),
                person.birthDate(), gender, person.relationType(), document(person.document(), path), person.address());
    }

    private static IdentityDocument document(ParticipantDocument document, String path) {
        IdentityDocumentType type = Enums.named(IdentityDocumentType.class, document.type())
                .orElseThrow(() -> new ParticipantDataException(UNKNOWN_DOCUMENT_TYPE, invalid(path + ".document.type")));
        return new IdentityDocument(type, document.series(), document.number(), document.issuedBy(),
                document.issuedAt(), document.divisionCode());
    }

    private static String invalid(String path) {
        return "недопустимое значение поля " + path;
    }
}
