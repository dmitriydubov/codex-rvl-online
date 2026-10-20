package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Insurer;

public record InsuranceContractResponse(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean isArchive,
        @Nullable Organization insurer) {

    public static InsuranceContractResponse of(InsuranceContract contract, @Nullable Insurer insurer) {
        return new InsuranceContractResponse(
                contract.id(), contract.externalId(), contract.number(),
                contract.beginDate(), contract.endDate(), contract.isArchive(),
                insurer == null ? null : Organization.of(insurer));
    }

    public record Organization(
            UUID id,
            UUID externalId,
            String name,
            @Nullable String fullName,
            @Nullable String inn,
            @Nullable String kpp) {

        static Organization of(Insurer insurer) {
            return new Organization(insurer.id(), insurer.externalId(), insurer.name(), insurer.fullName(),
                    insurer.inn(), insurer.kpp());
        }
    }
}
