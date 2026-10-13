package ru.projectx.contracts.service.lm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.IssuanceState;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.reference.InsuranceContractReads;
import ru.projectx.contracts.service.reference.PolicyReads;
import ru.projectx.contracts.service.reference.ProgramReads;
import ru.projectx.contracts.service.reference.ReferenceOutcome;
import ru.projectx.contracts.service.reference.ReferenceRead;

@Component
public class LmPolicyReferences {

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
            return new RequestResolution.Refused(IssueErrors.programMissing());
        }
        List<Supplier<ReferenceOutcome>> reads = List.of(
                () -> ReferenceRead.read(ReferenceKind.PROGRAM, state.programId().get(), programs::program),
                () -> ReferenceRead.read(ReferenceKind.INSURANCE_CONTRACT, state.request().insuranceContractId(),
                        insuranceContracts::insuranceContract),
                () -> ReferenceRead.read(ReferenceKind.POLICY, state.request().insurerPolicyId(), policies::policy));
        return resolution(reads);
    }

    private static RequestResolution resolution(List<Supplier<ReferenceOutcome>> reads) {
        List<ReferenceOutcome> outcomes = new ArrayList<>();
        for (Supplier<ReferenceOutcome> read : reads) {
            ReferenceOutcome outcome = read.get();
            if (outcome instanceof ReferenceOutcome.Unavailable unavailable) {
                return new RequestResolution.Unavailable(unavailable.kind());
            }
            outcomes.add(outcome);
        }
        return firstRefused(outcomes).orElseGet(() -> resolved(outcomes));
    }

    private static Optional<RequestResolution> firstRefused(List<ReferenceOutcome> outcomes) {
        return outcomes.stream()
                .filter(ReferenceOutcome.Refused.class::isInstance)
                .map(ReferenceOutcome.Refused.class::cast)
                .<RequestResolution>map(refused -> new RequestResolution.Refused(refused.error()))
                .findFirst();
    }

    private static RequestResolution resolved(List<ReferenceOutcome> outcomes) {
        return new RequestResolution.Resolved(new ExternalIdentifiers(found(outcomes.get(0)), found(outcomes.get(1)),
                found(outcomes.get(2))));
    }

    private static UUID found(ReferenceOutcome outcome) {
        return ((ReferenceOutcome.Found) outcome).externalId();
    }
}
