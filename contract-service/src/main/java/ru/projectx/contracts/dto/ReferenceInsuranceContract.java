package ru.projectx.contracts.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReferenceInsuranceContract(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean isArchive,
        @Nullable Insurer insurer) implements ReferenceRecord {

    public record Insurer(
            UUID id,
            UUID externalId,
            String name,
            @Nullable String fullName,
            @Nullable String inn,
            @Nullable String kpp) {
    }
}
