package ru.projectx.contracts.service.inbox;

import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.ParticipantDocument;
import ru.projectx.contracts.message.ParticipantPerson;
import ru.projectx.contracts.message.TaskParticipant;

final class ParticipantParser {

    private ParticipantParser() {
    }

    static TaskParticipant parse(JsonFields participant, int index) {
        TaskParticipant parsed = new TaskParticipant(index, participant.uuid("id"), participant.bool("is_relative"),
                participant.decimal("amount_due"), person(participant.requiredObject("personal_data")));
        if (parsed.amountDue().signum() < 0) {
            throw new ContractViolationException("поле " + participant.path() + ".amount_due отрицательное");
        }
        return parsed;
    }

    private static ParticipantPerson person(JsonFields person) {
        return new ParticipantPerson(person.text("last_name"), person.text("first_name"),
                person.textOrNull("middle_name"), person.date("birth_date"), person.text("gender"),
                person.textOrNull("relation_type"), document(person.requiredObject("document")),
                person.textOrNull("address"));
    }

    private static ParticipantDocument document(JsonFields document) {
        return new ParticipantDocument(document.text("type"), document.textOrNull("series"), document.text("number"),
                document.textOrNull("issued_by"), document.dateOrNull("issued_at"), document.textOrNull("division_code"));
    }
}
