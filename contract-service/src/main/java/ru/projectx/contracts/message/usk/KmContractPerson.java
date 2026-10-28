package ru.projectx.contracts.message.usk;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.message.ParticipantDocument;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KmContractPerson(
        UUID participantId,
        @Nullable Boolean isRelative,
        @Nullable String relationType,
        String lastName,
        String firstName,
        @Nullable String middleName,
        LocalDate birthDate,
        String gender,
        ParticipantDocument document,
        @Nullable String address) {
}
