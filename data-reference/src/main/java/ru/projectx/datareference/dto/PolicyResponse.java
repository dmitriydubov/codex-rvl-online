package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.entity.policy.PolicyProgram;
import ru.projectx.datareference.entity.policy.PolicyServiceLine;
import ru.projectx.datareference.entity.catalog.Program;

public record PolicyResponse(
        UUID id,
        UUID externalId,
        String number,
        @Nullable String lastName,
        @Nullable String firstName,
        @Nullable String middleName,
        @Nullable LocalDate dateOfBirth,
        @Nullable String gender,
        @Nullable String email,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean availability,
        boolean isArchive,
        boolean personalDataErased,
        @Nullable ContractRef contract,
        List<ProgramLine> programs) {

    public static PolicyResponse of(
            Policy policy, @Nullable InsuranceContract contract, Map<UUID, Program> rvlPrograms) {

        return RelativePolicy.of(policy, contract, rvlPrograms).withEmail(policy.details().email());
    }

    public record ContractRef(UUID id, UUID externalId, String number) {

        static ContractRef of(InsuranceContract contract) {
            return new ContractRef(contract.id(), contract.externalId(), contract.number());
        }
    }

    public record ProgramLine(
            NamedRef program,
            BigDecimal limitAmount,
            LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability,
            List<ServiceLine> services) {

        static ProgramLine of(PolicyProgram row, Program program, List<PolicyServiceLine> serviceLines) {
            List<ServiceLine> services = serviceLines.stream()
                    .filter(line -> row.externalProgramId().equals(line.externalProgramId()))
                    .map(ServiceLine::of)
                    .toList();
            return new ProgramLine(NamedRef.of(program, program.name()), row.limitAmount(), row.beginDate(), row.endDate(),
                    row.availability(), services);
        }
    }


    public record ServiceLine(
            int layerNumber,
            BigDecimal copayment,
            @Nullable LocalDate beginDate,
            @Nullable LocalDate endDate,
            boolean availability) {

        static ServiceLine of(PolicyServiceLine line) {
            return new ServiceLine(line.layerNumber(), line.copayment(), line.beginDate(), line.endDate(),
                    line.availability());
        }
    }
}
