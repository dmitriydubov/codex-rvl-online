package ru.projectx.contracts.entity;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PersonSnapshot(
        UUID participantId,
        String lastName,
        String firstName,
        @Nullable String middleName,
        LocalDate birthDate,
        Gender gender,
        @Nullable String relationType,
        IdentityDocument document,
        @Nullable String address) {
}
