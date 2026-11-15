package ru.projectx.datareference.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.dto.RelativePolicy;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.catalog.ProgramRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.service.ReadFixtures;

@ExtendWith(MockitoExtension.class)
class RelativePoliciesTest {

    private static final UUID CONTRACT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID UNKNOWN_CONTRACT = UUID.fromString("e0000000-0000-4000-8000-000000000002");
    private static final UUID SPOUSE = UUID.fromString("e0000000-0000-4000-8000-000000000003");
    private static final UUID CHILD = UUID.fromString("e0000000-0000-4000-8000-000000000004");
    private static final UUID RVL_PROGRAM = UUID.fromString("e0000000-0000-4000-8000-000000000005");
    private static final UUID CHILD_PROGRAM = UUID.fromString("e0000000-0000-4000-8000-000000000006");

    @Mock
    private PolicyRepository policies;

    @Mock
    private InsuranceContractRepository insuranceContracts;

    @Mock
    private ProgramRepository programs;

    private RelativePolicies relativePolicies;

    @BeforeEach
    void setUp() {
        relativePolicies = new RelativePolicies(policies, insuranceContracts, new RvlProgramCatalog(programs));
    }

    @Test
    void noRelatives_readsNothing() {
        assertThat(relativePolicies.byExternalIds(List.of())).isEmpty();

        verifyNoInteractions(policies, insuranceContracts, programs);
    }

    @Test
    void policies_areReadWithProgramsAndServiceLines_andCarryTheirOwnContractAndRvlPrograms() {
        List<UUID> ids = List.of(SPOUSE, CHILD);
        Policy spouse = ReadFixtures.policy(SPOUSE, "СТЕНД-0002", CONTRACT, List.of(RVL_PROGRAM), List.of(RVL_PROGRAM));
        Policy child = ReadFixtures.policy(CHILD, "СТЕНД-0003", UNKNOWN_CONTRACT, List.of(CHILD_PROGRAM), List.of());
        when(policies.findAllWithProgramsByExternalIdIn(ids)).thenReturn(List.of(spouse, child));
        when(insuranceContracts.findAllByExternalIdIn(List.of(CONTRACT, UNKNOWN_CONTRACT)))
                .thenReturn(List.of(ReadFixtures.contract(CONTRACT, null)));
        when(programs.findAllByExternalIdIn(Set.of(RVL_PROGRAM, CHILD_PROGRAM)))
                .thenReturn(List.of(ReadFixtures.program(RVL_PROGRAM, false), ReadFixtures.program(CHILD_PROGRAM, false)));

        Map<UUID, RelativePolicy> found = relativePolicies.byExternalIds(ids);

        verify(policies).findAllWithServiceLinesByExternalIdIn(ids);
        assertThat(found).containsOnlyKeys(SPOUSE, CHILD);
        assertThat(found.get(SPOUSE).number()).isEqualTo("СТЕНД-0002");
        assertThat(found.get(SPOUSE).contract()).isNotNull()
                .extracting(PolicyResponse.ContractRef::externalId).isEqualTo(CONTRACT);
        assertThat(found.get(SPOUSE).programs()).singleElement()
                .satisfies(line -> assertThat(line.services()).hasSize(1));
        assertThat(found.get(CHILD).contract()).isNull();
        assertThat(found.get(CHILD).programs()).extracting(line -> line.program().externalId())
                .containsExactly(CHILD_PROGRAM);
    }

    @Test
    void policiesSharingContract_readTheContractOnce() {
        List<UUID> ids = List.of(SPOUSE, CHILD);
        when(policies.findAllWithProgramsByExternalIdIn(ids)).thenReturn(List.of(
                ReadFixtures.policy(SPOUSE, "СТЕНД-0002", CONTRACT, List.of(), List.of()),
                ReadFixtures.policy(CHILD, "СТЕНД-0003", CONTRACT, List.of(), List.of())));
        when(insuranceContracts.findAllByExternalIdIn(any())).thenReturn(List.of(ReadFixtures.contract(CONTRACT, null)));

        relativePolicies.byExternalIds(ids);

        verify(insuranceContracts).findAllByExternalIdIn(List.of(CONTRACT));
        verifyNoInteractions(programs);
    }
}
