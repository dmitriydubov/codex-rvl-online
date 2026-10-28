package ru.projectx.contracts.message.usk;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.message.ParticipantDocument;

public record InsuredPerson(
        UUID participantId,
        @Nullable String relationType,
        String lastName,
        String firstName,
        @Nullable String middleName,
        LocalDate birthDate,
        String gender,
        ParticipantDocument document,
        @Nullable String address) {
}
