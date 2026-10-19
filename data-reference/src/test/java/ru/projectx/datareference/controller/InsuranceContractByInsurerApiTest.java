package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Insurer;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class InsuranceContractByInsurerApiTest {

    private static final String BY_INSURER = InternalApi.BASE_PATH + "/insurance-contracts?insurer_external_id={insurer}";
    private static final UUID INSURER = UUID.fromString("5e1d0000-0000-4000-8000-000000000001");
    private static final UUID OTHER_INSURER = UUID.fromString("5e1d0000-0000-4000-8000-000000000002");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private InsuranceContractRepository insuranceContracts;

    @Autowired
    private InsurerRepository insurers;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void insurerWithOneActiveAmongInactive_getsThatContractWithInsurer() {
        insurers.save(Insurer.create(INSURER,
                new Insurer.Details("АО «Работодатель»", "Акционерное общество «Работодатель»", null, null, false), NOW));
        saved("ДМС-ИСТЁК", TODAY.minusYears(1), TODAY.minusDays(1), INSURER, false);
        saved("ДМС-БУДУЩИЙ", TODAY.plusDays(1), null, INSURER, false);
        saved("ДМС-АРХИВ", TODAY.minusYears(1), null, INSURER, true);
        saved("ДМС-СОСЕДА", TODAY.minusYears(1), null, OTHER_INSURER, false);
        InsuranceContract active = saved("ДМС-ДЕЙСТВУЕТ", TODAY, TODAY, INSURER, false);

        MvcTestResult result = mvc.get().uri(BY_INSURER, INSURER).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(active.id().toString());
        assertThat(result).bodyJson().extractingPath("$.external_id").isEqualTo(active.externalId().toString());
        assertThat(result).bodyJson().extractingPath("$.number").isEqualTo("ДМС-ДЕЙСТВУЕТ");
        assertThat(result).bodyJson().extractingPath("$.insurer.external_id").isEqualTo(INSURER.toString());
        assertThat(result).bodyJson().extractingPath("$.insurer.name").isEqualTo("АО «Работодатель»");
    }

    @Test
    void insurerWithTwoActive_isConflictListingBothContractIds() {
        InsuranceContract first = saved("ДМС-1", TODAY.minusYears(1), null, INSURER, false);
        InsuranceContract second = saved("ДМС-2", TODAY, TODAY.plusYears(1), INSURER, false);
        saved("ДМС-ИСТЁК", TODAY.minusYears(2), TODAY.minusYears(1), INSURER, false);

        MvcTestResult result = mvc.get().uri(BY_INSURER, INSURER).exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("AMBIGUOUS_INSURANCE_CONTRACT");
        assertThat(result).bodyJson().extractingPath("$.error.details[*].insurance_contract_id").asArray()
                .containsExactlyInAnyOrder(first.id().toString(), second.id().toString());
    }

    @Test
    void insurerWithoutActiveContract_isInsuranceContractNotFound() {
        saved("ДМС-ИСТЁК", TODAY.minusYears(1), TODAY.minusDays(1), INSURER, false);

        MvcTestResult result = mvc.get().uri(BY_INSURER, INSURER).exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
    }

    private InsuranceContract saved(
            String number, LocalDate begin, @Nullable LocalDate end, UUID insurer, boolean archived) {

        return insuranceContracts.save(InsuranceContract.create(UUID.randomUUID(),
                new InsuranceContract.Details(number, begin, end, insurer, archived), NOW));
    }
}
