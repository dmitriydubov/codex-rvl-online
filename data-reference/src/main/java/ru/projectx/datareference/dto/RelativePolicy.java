package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RelativePolicy(
        UUID id,
        UUID externalId,
        String number,
        @Nullable String lastName,
        @Nullable String firstName,
        @Nullable String middleName,
        @Nullable LocalDate dateOfBirth,
        @Nullable String gender,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean availability,
        boolean isArchive,
        boolean personalDataErased,
        PolicyResponse.@Nullable ContractRef contract,
        List<PolicyResponse.ProgramLine> programs) {

    public static RelativePolicy of(PolicyResponse policy) {
        return new RelativePolicy(policy.id(), policy.externalId(), policy.number(),
                policy.lastName(), policy.firstName(), policy.middleName(), policy.dateOfBirth(), policy.gender(),
                policy.beginDate(), policy.endDate(), policy.availability(), policy.isArchive(),
                policy.personalDataErased(), policy.contract(), policy.programs());
    }
}
