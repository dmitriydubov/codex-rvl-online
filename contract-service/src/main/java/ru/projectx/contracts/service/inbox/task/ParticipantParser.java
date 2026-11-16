package ru.projectx.contracts.service.inbox.task;

import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.ParticipantDocument;
import ru.projectx.contracts.message.task.ParticipantPerson;
import ru.projectx.contracts.message.task.TaskParticipant;
import ru.projectx.contracts.service.inbox.parse.JsonFields;

final class ParticipantParser {

    private ParticipantParser() {
    }

    static TaskParticipant parse(JsonFields participant, int index) {
        TaskParticipant parsed = new TaskParticipant(index, participant.field("id").uuid(),
                participant.field("is_relative").bool(), participant.field("amount_due").decimal(),
                participant.field("program_id").optional().uuid(), person(participant.requiredObject("personal_data")));
        if (parsed.amountDue().signum() < 0) {
            throw new ContractViolationException("поле " + participant.path() + ".amount_due отрицательное");
        }
        return parsed;
    }

    private static ParticipantPerson person(JsonFields person) {
        return new ParticipantPerson(person.field("last_name").text(), person.field("first_name").text(),
                person.field("middle_name").optional().text(), person.field("birth_date").date(),
                person.field("gender").text(), person.field("relation_type").optional().text(),
                document(person.requiredObject("document")), person.field("address").optional().text());
    }

    private static ParticipantDocument document(JsonFields document) {
        return new ParticipantDocument(document.field("type").text(), document.field("series").optional().text(),
                document.field("number").text(), document.field("issued_by").optional().text(),
                document.field("issued_at").optional().date(), document.field("division_code").optional().text());
    }
}
