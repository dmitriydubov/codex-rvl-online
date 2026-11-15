package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.policy.InsuredAssociation;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.entity.policy.PolicyProgram;
import ru.projectx.datareference.entity.policy.PolicyServiceLine;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.catalog.ProgramRepository;
import ru.projectx.datareference.repository.policy.InsuredAssociationRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class InsuredRelativesApiTest {

    private static final String RELATIVES = InternalApi.BASE_PATH + "/policies/relatives?external_id={id}";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final UUID CONTRACT = UUID.fromString("5a1e0000-0000-4000-8000-0000000000c1");
    private static final UUID PROGRAM = UUID.fromString("5a1e0000-0000-4000-8000-0000000000b1");
    private static final UUID INSURED = UUID.fromString("5a1e0000-0000-4000-8000-000000000001");
    private static final UUID SPOUSE = UUID.fromString("5a1e0000-0000-4000-8000-000000000002");
    private static final UUID CHILD = UUID.fromString("5a1e0000-0000-4000-8000-000000000003");
    private static final UUID PARENT = UUID.fromString("5a1e0000-0000-4000-8000-000000000004");
    private static final UUID FORMER = UUID.fromString("5a1e0000-0000-4000-8000-000000000005");
    private static final UUID NOT_ARRIVED = UUID.fromString("5a1e0000-0000-4000-8000-000000000006");
    private static final UUID TYPE = UUID.fromString("797093be-2c51-11e8-8123-005056b43f21");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private PolicyRepository policies;

    @Autowired
    private InsuredAssociationRepository associations;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void prepareReference(@Autowired InsuranceContractRepository contracts, @Autowired ProgramRepository programs) {
        databaseCleaner.clear();
        contracts.save(InsuranceContract.create(CONTRACT, new InsuranceContract.Details("26 LM 0002",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, false), NOW));
        programs.save(Program.create(PROGRAM, new Program.Details(null, null, "Санаторно-курортное лечение", "СКЛ",
                false), NOW));
        savePolicy(INSURED, "СТЕНД-0001");
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void stopCounting() {
        statistics.setStatisticsEnabled(false);
    }

    @Test
    void relatives_activeToday_comeWithTheirPolicyContractAndPrograms_withoutEmail() {
        savePolicy(SPOUSE, "СТЕНД-0002");
        savePolicy(FORMER, "СТЕНД-0005");
        link(SPOUSE, TODAY, TODAY);
        link(FORMER, TODAY.minusYears(1), TODAY.minusDays(1));
        link(NOT_ARRIVED, TODAY, null);

        MvcTestResult result = mvc.get().uri(RELATIVES, INSURED).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].policy.number").asArray().containsExactly("СТЕНД-0002");
        assertThat(result).bodyJson().extractingPath("$.items[0].relation_type_external_id").isEqualTo(TYPE.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].policy.last_name").isEqualTo("Тестова");
        assertThat(result).bodyJson().extractingPath("$.items[0].policy.contract.number").isEqualTo("26 LM 0002");
        assertThat(result).bodyJson().extractingPath("$.items[0].policy.programs[0].services[0].copayment")
                .isEqualTo(20.00);
        assertThat(result).bodyJson().extractingPath("$.items[0].policy").asMap().doesNotContainKey("email");
    }

    @Test
    void relatives_areReadWithTheSameNumberOfStatements_howeverManyThereAre() {
        savePolicy(SPOUSE, "СТЕНД-0002");
        link(SPOUSE, TODAY, null);
        long forOne = statementsToRead();
        savePolicy(CHILD, "СТЕНД-0003");
        savePolicy(PARENT, "СТЕНД-0004");
        link(CHILD, TODAY, null);
        link(PARENT, TODAY, null);

        long forThree = statementsToRead();

        assertThat(forThree).isEqualTo(forOne);
    }

    private long statementsToRead() {
        statistics.clear();
        MvcTestResult result = mvc.get().uri(RELATIVES, INSURED).exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].policy.programs[0].services").asArray().isNotEmpty();
        return statistics.getPrepareStatementCount();
    }

    private void link(UUID related, LocalDate validFrom, @Nullable LocalDate validTo) {
        associations.save(InsuredAssociation.of(new InsuredAssociation.Row(INSURED, related, TYPE, validFrom, validTo),
                NOW));
    }

    private void savePolicy(UUID externalId, String number) {
        var details = new Policy.Details(CONTRACT, number, "Тестова", "Проба", null, LocalDate.of(2015, 1, 1), "Ж",
                "relative@example.test", LocalDate.of(2026, 1, 1), null, true, false);
        var programs = List.of(new PolicyProgram.Row(PROGRAM, LocalDate.of(2026, 1, 1), null, null, null, null, null,
                new BigDecimal("150000.00"), true));
        var lines = List.of(new PolicyServiceLine.Row(PROGRAM, 1, new BigDecimal("20.00"), LocalDate.of(2026, 1, 1),
                null, true));
        policies.save(Policy.create(externalId, new Policy.Snapshot(details, programs, lines), NOW));
    }
}
