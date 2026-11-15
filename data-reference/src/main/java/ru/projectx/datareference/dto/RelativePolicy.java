package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.policy.Policy;

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

    public static RelativePolicy of(
            Policy policy, @Nullable InsuranceContract contract, Map<UUID, Program> rvlPrograms) {

        List<PolicyResponse.ProgramLine> programs = policy.programs().stream()
                .filter(row -> rvlPrograms.containsKey(row.externalProgramId()))
                .map(row -> PolicyResponse.ProgramLine.of(row, rvlPrograms.get(row.externalProgramId()),
                        policy.serviceLines()))
                .toList();
        Policy.Details details = policy.details();
        return new RelativePolicy(
                policy.id(), policy.externalId(), details.number(),
                details.lastName(), details.firstName(), details.middleName(),
                details.dateOfBirth(), details.gender(),
                details.beginDate(), details.endDate(), details.availability(), policy.isArchive(),
                policy.personalDataErased(), contract == null ? null : PolicyResponse.ContractRef.of(contract),
                programs);
    }

    PolicyResponse withEmail(@Nullable String email) {
        return new PolicyResponse(id, externalId, number, lastName, firstName, middleName, dateOfBirth, gender, email,
                beginDate, endDate, availability, isArchive, personalDataErased, contract, programs);
    }
}
