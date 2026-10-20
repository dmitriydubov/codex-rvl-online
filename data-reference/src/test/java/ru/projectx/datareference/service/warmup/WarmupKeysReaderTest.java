package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.repository.ReferenceId;
import ru.projectx.datareference.repository.catalog.InsuranceContractKey;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import ru.projectx.datareference.repository.catalog.ProgramRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;

@ExtendWith(MockitoExtension.class)
class WarmupKeysReaderTest {

    private static final int RECORDS = 7;

    @Mock
    private InsuranceContractRepository insuranceContracts;
    @Mock
    private LpuRepository lpu;
    @Mock
    private ProgramRepository programs;
    @Mock
    private PolicyRepository policies;

    @Test
    void readsUpToTheLimitOfEachReference() {
        InsuranceContractKey contract = new InsuranceContractKey(UUID.randomUUID(), UUID.randomUUID(), null);
        ReferenceId sanatorium = new ReferenceId(UUID.randomUUID());
        ReferenceId program = new ReferenceId(UUID.randomUUID());
        ReferenceId policy = new ReferenceId(UUID.randomUUID());
        when(insuranceContracts.findByArchiveFalseOrderByIdAsc(Limit.of(RECORDS))).thenReturn(List.of(contract));
        when(lpu.findByArchiveFalseOrderByIdAsc(Limit.of(RECORDS))).thenReturn(List.of(sanatorium));
        when(programs.findByArchiveFalseOrderByIdAsc(Limit.of(RECORDS))).thenReturn(List.of(program));
        when(policies.findByArchiveFalseOrderByIdAsc(Limit.of(RECORDS))).thenReturn(List.of(policy));

        WarmupKeys keys = new WarmupKeysReader(insuranceContracts, lpu, programs, policies).read(RECORDS);

        assertThat(keys).isEqualTo(
                new WarmupKeys(List.of(contract), List.of(sanatorium), List.of(program), List.of(policy)));
    }
}
