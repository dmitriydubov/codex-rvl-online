package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.LpuContract;

public record PriceContractRef(
        UUID id,
        UUID externalId,
        @Nullable String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean uploadedToSob,
        Kind kind) {

    public enum Kind {
        MAIN, DOPS
    }

    public static PriceContractRef of(LpuContract contract, Kind kind) {
        return new PriceContractRef(contract.id(), contract.externalId(), contract.number(),
                contract.beginDate(), contract.endDate(), contract.uploadedToSob(), kind);
    }
}
