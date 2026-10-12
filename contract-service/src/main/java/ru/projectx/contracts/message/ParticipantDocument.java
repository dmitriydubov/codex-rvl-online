package ru.projectx.contracts.message;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record ParticipantDocument(
        String type,
        @Nullable String series,
        String number,
        @Nullable String issuedBy,
        @Nullable LocalDate issuedAt,
        @Nullable String divisionCode) {
}
