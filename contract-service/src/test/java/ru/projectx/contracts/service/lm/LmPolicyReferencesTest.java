package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.ReferenceInsuranceContract;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.dto.ReferenceProgram;
import ru.projectx.contracts.entity.IssuanceState;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.service.reference.InsuranceContractReads;
import ru.projectx.contracts.service.reference.PolicyReads;
import ru.projectx.contracts.service.reference.ProgramReads;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class LmPolicyReferencesTest {

    private static final UUID PROGRAM_EXTERNAL = UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2");
    private static final UUID CONTRACT_EXTERNAL = UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88");
    private static final UUID POLICY_EXTERNAL = UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41");

    private final ProgramReads programs = mock();
    private final InsuranceContractReads insuranceContracts = mock();
    private final PolicyReads policies = mock();
    private final LmPolicyReferences references = new LmPolicyReferences(programs, insuranceContracts, policies);
    private final IssuanceState state = Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD).state();

    @BeforeEach
    void referencesAreLive() {
        given(programs.program(Tasks.PROGRAM)).willReturn(new ReferenceProgram(Tasks.PROGRAM, PROGRAM_EXTERNAL,
                "Стенд", null, false, List.of()));
        given(insuranceContracts.insuranceContract(Tasks.INSURANCE_CONTRACT)).willReturn(new ReferenceInsuranceContract(
                Tasks.INSURANCE_CONTRACT, CONTRACT_EXTERNAL, "СТЕНД-ДМС-0001", LocalDate.parse("2026-01-01"), null,
                false, null));
        given(policies.policy(Tasks.INSURER_POLICY)).willReturn(new ReferencePolicy(Tasks.INSURER_POLICY,
                POLICY_EXTERNAL, "СТЕНД-0001", LocalDate.parse("2026-01-01"), null, false));
    }

    @Test
    void liveReferences_resolveToTheirExternalIdentifiers() {
        assertThat(references.resolve(state)).isEqualTo(new RequestResolution.Resolved(
                new ExternalIdentifiers(PROGRAM_EXTERNAL, CONTRACT_EXTERNAL, POLICY_EXTERNAL)));
    }

    @Test
    void archivedReference_refusesTheRequestWithTheError() {
        given(insuranceContracts.insuranceContract(Tasks.INSURANCE_CONTRACT))
                .willThrow(new ReferenceArchivedException(ReferenceKind.INSURANCE_CONTRACT, Tasks.INSURANCE_CONTRACT));

        assertThat(references.resolve(state)).isEqualTo(new RequestResolution.Refused(new IssueError(
                "INSURANCE_CONTRACT_ARCHIVED",
                "договор страхования " + Tasks.INSURANCE_CONTRACT + " в архиве справочного сервиса")));
    }

    @Test
    void unavailableReference_postponesTheRequestNamingTheKindEvenWhenAnotherIsRefused() {
        given(programs.program(Tasks.PROGRAM))
                .willThrow(new ReferenceArchivedException(ReferenceKind.PROGRAM, Tasks.PROGRAM));
        given(policies.policy(Tasks.INSURER_POLICY))
                .willThrow(new ReferenceUnavailableException(ReferenceUnavailableException.Reason.SERVICE_UNAVAILABLE));

        assertThat(references.resolve(state)).isEqualTo(new RequestResolution.Unavailable(ReferenceKind.POLICY));
    }

    @Test
    void unavailableProgram_stopsReadingTheOtherReferences() {
        given(programs.program(Tasks.PROGRAM))
                .willThrow(new ReferenceUnavailableException(ReferenceUnavailableException.Reason.TIMEOUT));

        assertThat(references.resolve(state)).isEqualTo(new RequestResolution.Unavailable(ReferenceKind.PROGRAM));
        verifyNoInteractions(insuranceContracts, policies);
    }

    @Test
    void policyWithoutProgram_isRefusedWithoutReadingAnything() {
        IssuanceState withoutProgram = new IssuanceState(state.request(), state.policyholderParticipantId(),
                Optional.empty(), state.period(), Optional.empty(), Optional.empty(), 1);

        assertThat(references.resolve(withoutProgram)).isEqualTo(new RequestResolution.Refused(
                new IssueError("PROGRAM_REQUIRED", "у записи о выпуске нет программы страхования")));
        verifyNoInteractions(programs, insuranceContracts, policies);
    }
}
