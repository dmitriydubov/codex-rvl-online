package ru.projectx.datareference.service.warmup;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import ru.projectx.datareference.repository.catalog.ProgramRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
public class WarmupKeysReader {

    private final InsuranceContractRepository insuranceContracts;
    private final LpuRepository lpu;
    private final ProgramRepository programs;
    private final PolicyRepository policies;

    public WarmupKeysReader(InsuranceContractRepository insuranceContracts, LpuRepository lpu,
            ProgramRepository programs, PolicyRepository policies) {
        this.insuranceContracts = insuranceContracts;
        this.lpu = lpu;
        this.programs = programs;
        this.policies = policies;
    }

    @RestReadTransaction
    public WarmupKeys read(int recordsPerReference) {
        Limit limit = Limit.of(recordsPerReference);
        return new WarmupKeys(
                insuranceContracts.findByArchiveFalseOrderByIdAsc(limit),
                lpu.findByArchiveFalseOrderByIdAsc(limit),
                programs.findByArchiveFalseOrderByIdAsc(limit),
                policies.findByArchiveFalseOrderByIdAsc(limit));
    }
}
