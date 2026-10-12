package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PolicyContractRow(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean archive) {
}
