package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.party.Gender;
import ru.projectx.contracts.entity.party.IdentityDocument;
import ru.projectx.contracts.entity.party.IdentityDocumentType;
import ru.projectx.contracts.entity.party.PersonSnapshot;
import ru.projectx.contracts.exception.ParticipantDataException;
import ru.projectx.contracts.message.ParticipantPerson;
import ru.projectx.contracts.message.TaskParticipant;
import ru.projectx.contracts.support.Tasks;

class PersonSnapshotsTest {

    @Test
    void snapshot_copiesEveryRequisiteOfTheParticipant() {
        PersonSnapshot snapshot = PersonSnapshots.of(Tasks.spouse("34000.00"));

        assertThat(snapshot).isEqualTo(new PersonSnapshot(Tasks.SPOUSE, "Тестова", "Тест", "Тестович",
                LocalDate.parse("1984-03-11"), Gender.MALE, "SPOUSE",
                new IdentityDocument(IdentityDocumentType.RF_PASSPORT, "4510", "123456", "Тестовый орган",
                        LocalDate.parse("2010-04-20"), "770-001"), "Тестовый адрес"));
    }

    @Test
    void insuredPerson_needsNoRelationType() {
        assertThat(PersonSnapshots.of(Tasks.insured("0.00")).relationType()).isNull();
    }

    @Test
    void relativeWithoutRelationType_isParticipantErrorNamingTheField() {
        TaskParticipant relative = Tasks.relative(1, Tasks.SPOUSE, "0.00", null);

        assertThatThrownBy(() -> PersonSnapshots.of(relative))
                .isInstanceOf(ParticipantDataException.class)
                .hasMessage("поле participants[1].personal_data.relation_type обязательно для родственника")
                .extracting("code").isEqualTo("RELATION_TYPE_REQUIRED");
    }

    @Test
    void unknownDocumentType_isParticipantErrorWithoutTheValue() {
        TaskParticipant participant = withDocumentType(Tasks.spouse("0.00"), "MILITARY_ID");

        assertThatThrownBy(() -> PersonSnapshots.of(participant))
                .isInstanceOf(ParticipantDataException.class)
                .hasMessage("недопустимое значение поля participants[1].personal_data.document.type")
                .extracting("code").isEqualTo("UNKNOWN_DOCUMENT_TYPE");
    }

    @Test
    void unknownGender_isParticipantErrorWithoutTheValue() {
        ParticipantPerson person = Tasks.person("Тестова", "SPOUSE");
        TaskParticipant participant = new TaskParticipant(2, Tasks.SPOUSE, true, BigDecimal.ZERO, Tasks.PROGRAM,
                new ParticipantPerson(person.lastName(), person.firstName(), person.middleName(), person.birthDate(),
                        "UNKNOWN", person.relationType(), person.document(), person.address()));

        assertThatThrownBy(() -> PersonSnapshots.of(participant))
                .isInstanceOf(ParticipantDataException.class)
                .hasMessage("недопустимое значение поля participants[2].personal_data.gender")
                .extracting("code").isEqualTo("UNKNOWN_GENDER");
    }

    private static TaskParticipant withDocumentType(TaskParticipant participant, String type) {
        ParticipantPerson person = participant.person();
        return new TaskParticipant(participant.index(), participant.id(), participant.relative(),
                participant.amountDue(), participant.programId(), new ParticipantPerson(person.lastName(), person.firstName(),
                        person.middleName(), person.birthDate(), person.gender(), person.relationType(),
                        Tasks.document(type), person.address()));
    }
}
