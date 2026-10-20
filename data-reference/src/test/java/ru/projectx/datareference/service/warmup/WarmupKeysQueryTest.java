package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.ReferenceId;
import ru.projectx.datareference.repository.catalog.InsuranceContractKey;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import ru.projectx.datareference.repository.catalog.ProgramRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.service.ReadFixtures;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class WarmupKeysQueryTest {

    private static final UUID INSURER = UUID.fromString("d23f3088-f547-4755-b93e-ae192a89d166");

    @Autowired
    private WarmupKeysReader reader;
    @Autowired
    private InsuranceContractRepository insuranceContracts;
    @Autowired
    private LpuRepository lpu;
    @Autowired
    private ProgramRepository programs;
    @Autowired
    private PolicyRepository policies;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void contractKey_carriesItsIdGuidAndInsurerGuid() {
        InsuranceContract contract = insuranceContracts.save(ReadFixtures.contract(UUID.randomUUID(), INSURER));

        assertThat(reader.read(10).insuranceContracts())
                .containsExactly(new InsuranceContractKey(contract.id(), contract.externalId(), INSURER));
    }

    @Test
    void archivedRecords_areNotWarmed() {
        Lpu active = lpu.save(ReadFixtures.lpu(UUID.randomUUID(), false));
        lpu.save(ReadFixtures.lpu(UUID.randomUUID(), true));
        Program current = programs.save(ReadFixtures.program(UUID.randomUUID(), false));
        programs.save(ReadFixtures.program(UUID.randomUUID(), true));

        WarmupKeys keys = reader.read(10);

        assertThat(keys.lpu()).containsExactly(new ReferenceId(active.id()));
        assertThat(keys.programs()).containsExactly(new ReferenceId(current.id()));
    }

    @Test
    void eachReference_isLimitedToTheRecordsPerReference() {
        List<UUID> saved = List.of(save(), save(), save());

        List<ReferenceId> warmed = reader.read(2).policies();

        assertThat(warmed).hasSize(2);
        assertThat(saved).containsAll(warmed.stream().map(ReferenceId::id).toList());
    }

    private UUID save() {
        Policy policy = policies.save(ReadFixtures.policy(UUID.randomUUID(), List.of(), List.of()));
        return policy.id();
    }
}
