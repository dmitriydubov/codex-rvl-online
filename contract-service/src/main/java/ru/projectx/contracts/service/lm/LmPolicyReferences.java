package ru.projectx.contracts.service.lm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.IssuanceState;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.service.reference.InsuranceContractReads;
import ru.projectx.contracts.service.reference.PolicyReads;
import ru.projectx.contracts.service.reference.ProgramReads;
import ru.projectx.contracts.service.reference.ReferenceOutcome;
import ru.projectx.contracts.service.reference.ReferenceRead;

@Component
public class LmPolicyReferences {

    static final String PROGRAM_REQUIRED = "PROGRAM_REQUIRED";
    static final String NO_PROGRAM = "у полиса ЛМ нет программы страхования";

    private final ProgramReads programs;
    private final InsuranceContractReads insuranceContracts;
    private final PolicyReads policies;

    public LmPolicyReferences(ProgramReads programs, InsuranceContractReads insuranceContracts, PolicyReads policies) {
        this.programs = programs;
        this.insuranceContracts = insuranceContracts;
        this.policies = policies;
    }

    public RequestResolution resolve(IssuanceState state) {
        if (state.programId().isEmpty()) {
            return new RequestResolution.Refused(new IssueError(PROGRAM_REQUIRED, NO_PROGRAM));
        }
        List<ReferenceOutcome> outcomes = List.of(
                ReferenceRead.read(ReferenceKind.PROGRAM, state.programId().get(), programs::program),
                ReferenceRead.read(ReferenceKind.INSURANCE_CONTRACT, state.request().insuranceContractId(),
                        insuranceContracts::insuranceContract),
                ReferenceRead.read(ReferenceKind.POLICY, state.request().insurerPolicyId(), policies::policy));
        return resolution(outcomes);
    }

    private static RequestResolution resolution(List<ReferenceOutcome> outcomes) {
        return firstUnavailable(outcomes)
                .or(() -> firstRefused(outcomes))
                .orElseGet(() -> new RequestResolution.Resolved(new ExternalIdentifiers(found(outcomes.get(0)),
                        found(outcomes.get(1)), found(outcomes.get(2)))));
    }

    private static Optional<RequestResolution> firstUnavailable(List<ReferenceOutcome> outcomes) {
        return outcomes.stream()
                .filter(ReferenceOutcome.Unavailable.class::isInstance)
                .map(ReferenceOutcome.Unavailable.class::cast)
                .<RequestResolution>map(unavailable -> new RequestResolution.Unavailable(unavailable.kind()))
                .findFirst();
    }

    private static Optional<RequestResolution> firstRefused(List<ReferenceOutcome> outcomes) {
        return outcomes.stream()
                .filter(ReferenceOutcome.Refused.class::isInstance)
                .map(ReferenceOutcome.Refused.class::cast)
                .<RequestResolution>map(refused -> new RequestResolution.Refused(refused.error()))
                .findFirst();
    }

    private static UUID found(ReferenceOutcome outcome) {
        return ((ReferenceOutcome.Found) outcome).externalId();
    }
}
