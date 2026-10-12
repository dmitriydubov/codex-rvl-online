package ru.projectx.contracts.entity;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record IdentityDocument(
        IdentityDocumentType type,
        @Nullable String series,
        String number,
        @Nullable String issuedBy,
        @Nullable LocalDate issuedAt,
        @Nullable String divisionCode) {
}
