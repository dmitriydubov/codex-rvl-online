package ru.projectx.contracts.message;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
