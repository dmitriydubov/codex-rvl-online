package ru.projectx.contracts.message.task;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.message.ParticipantDocument;

public record ParticipantPerson(
        String lastName,
        String firstName,
        @Nullable String middleName,
        LocalDate birthDate,
        String gender,
        @Nullable String relationType,
        ParticipantDocument document,
        @Nullable String address) {
}
