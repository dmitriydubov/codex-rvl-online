package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * R-05 (полис целиком по трём ключам) и R-04 (поиск по ФИО и номеру) поверх настоящей
 * базы. Часы остановлены на {@link ru.projectx.datareference.support.FixedClockConfig#NOW}:
 * по Москве это 14.03.2026, и «действующий на сегодня» считается от этого дня.
 */
@IntegrationTest
class PolicyApiTest {

    private static final String BASE = InternalApi.BASE_PATH;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final UUID CONTRACT_EXT = UUID.fromString("5a1d0000-0000-4000-8000-0000000000c1");
    private static final UUID RVL_PROGRAM_EXT = UUID.fromString("5a1d0000-0000-4000-8000-0000000000b1");
    private static final UUID ARCHIVED_PROGRAM_EXT = UUID.fromString("5a1d0000-0000-4000-8000-0000000000b2");
    private static final UUID UNKNOWN_PROGRAM_EXT = UUID.fromString("5a1d0000-0000-4000-8000-0000000000b3");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private InsuranceContractRepository insuranceContractRepository;

    private InsuranceContract contract;

    @BeforeEach
    void prepareReference() {
        databaseCleaner.clear();
        contract = insuranceContractRepository.save(InsuranceContract.create(CONTRACT_EXT,
                new InsuranceContract.Details("26 LM 0001", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                        null, false), NOW));
        programRepository.save(Program.create(RVL_PROGRAM_EXT,
                new Program.Details(null, null, "Санаторно-курортное лечение", "СКЛ", false), NOW));
        programRepository.save(Program.create(ARCHIVED_PROGRAM_EXT,
                new Program.Details(null, null, "Архивная программа", "СКЛ", true), NOW));
    }

    @Test
    void policyById_returnsPersonalDataContractAndRvlProgramsOnly() {
        Policy policy = savePolicy("П-000001", "Петров", "Иван", "Сергеевич", TODAY.minusMonths(1), null,
                List.of(RVL_PROGRAM_EXT, ARCHIVED_PROGRAM_EXT, UNKNOWN_PROGRAM_EXT));

        MvcTestResult result = mvc.get().uri(BASE + "/policies/{id}", policy.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.external_id").isEqualTo(policy.externalId().toString());
        assertThat(result).bodyJson().extractingPath("$.last_name").isEqualTo("Петров");
        assertThat(result).bodyJson().extractingPath("$.date_of_birth").isEqualTo("1984-03-11");
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("petrov@example.test");
        assertThat(result).bodyJson().extractingPath("$.contract.id").isEqualTo(contract.id().toString());
        assertThat(result).bodyJson().extractingPath("$.contract.number").isEqualTo("26 LM 0001");
        assertThat(result).bodyJson().extractingPath("$.programs").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.programs[0].program.external_id")
                .isEqualTo(RVL_PROGRAM_EXT.toString());
        assertThat(result).bodyJson().extractingPath("$.programs[0].limit_amount").isEqualTo(150000.00);
        assertThat(result).bodyJson().extractingPath("$.programs[0].services").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.programs[0].services[0].copayment").isEqualTo(20.00);
    }

    @Test
    void policyByExternalId_isFound() {
        Policy policy = savePolicy("П-000002", "Сидорова", "Анна", null, TODAY.minusMonths(1), null,
                List.of(RVL_PROGRAM_EXT));

        MvcTestResult result = mvc.get().uri(BASE + "/policies?external_id={ext}", policy.externalId()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(policy.id().toString());
    }

    @Test
    void policyByNumberAndContract_isFound_andDuplicatesGive409() {
        Policy first = savePolicy("П-000003", "Кузнецов", "Олег", null, TODAY.minusMonths(1), null,
                List.of(RVL_PROGRAM_EXT));

        MvcTestResult found = mvc.get().uri(BASE + "/policies?number={n}&contract_id={c}", "П-000003", contract.id())
                .exchange();
        assertThat(found).hasStatusOk();
        assertThat(found).bodyJson().extractingPath("$.id").isEqualTo(first.id().toString());

        Policy duplicate = savePolicy("П-000003", "Кузнецов", "Олег", null, TODAY.minusMonths(1), null,
                List.of(RVL_PROGRAM_EXT));
        MvcTestResult conflict = mvc.get().uri(BASE + "/policies?number={n}&contract_id={c}", "П-000003", contract.id())
                .exchange();
        assertThat(conflict).hasStatus(409);
        assertThat(conflict).bodyJson().extractingPath("$.error.code").isEqualTo("AMBIGUOUS_POLICY");
        assertThat(conflict).bodyJson().extractingPath("$.error.details").asArray().hasSize(2);
        assertThat(conflict).bodyJson().extractingPath("$.error.details[*].policy_id").asArray()
                .containsExactlyInAnyOrder(first.id().toString(), duplicate.id().toString());
    }

    @Test
    void policyByNumber_unknownContract_returns404_andMissingKey_returns400() {
        MvcTestResult unknown = mvc.get().uri(BASE + "/policies?number={n}&contract_id={c}", "П-1", UUID.randomUUID())
                .exchange();
        assertThat(unknown).hasStatus(404);
        assertThat(unknown).bodyJson().extractingPath("$.error.code").isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");

        MvcTestResult noKey = mvc.get().uri(BASE + "/policies").exchange();
        assertThat(noKey).hasStatus(400);
        assertThat(noKey).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
    }

    @Test
    void search_byNameTokens_findsBothOrdersAndRanksLastAndFirstName() {
        savePolicy("П-000010", "Петров", "Иван", "Сергеевич", TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));
        savePolicy("П-000011", "Петрова", "Анна", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));
        savePolicy("П-000012", "Иванов", "Пётр", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));

        MvcTestResult bothOrders = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}",
                CONTRACT_EXT, "иван петр").exchange();
        assertThat(bothOrders).hasStatusOk();
        assertThat(bothOrders).bodyJson().extractingPath("$.items[*].last_name").asArray()
                .containsExactly("Иванов", "Петров");

        MvcTestResult byPrefix = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}",
                CONTRACT_EXT, "  Петр ").exchange();
        assertThat(byPrefix).bodyJson().extractingPath("$.items[*].last_name").asArray()
                .as("фамилия важнее имени, внутри группы — по фамилии")
                .containsExactly("Петров", "Петрова", "Иванов");
        assertThat(byPrefix).bodyText().doesNotContain("@example.test").doesNotContain("1984-03-11");
    }

    @Test
    void search_byDigits_findsNumberAndRanksExactFirst() {
        savePolicy("26 KM R/016039/1", "Орлов", "Пётр", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));
        savePolicy("26 KM R/016039/12", "Быков", "Илья", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));

        MvcTestResult result = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}",
                CONTRACT_EXT, "26 km r/016039/1").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].number").asArray()
                .containsExactly("26 KM R/016039/1", "26 KM R/016039/12");
    }

    /** Базовая логика A: архив, истёкшие, будущие и полисы без программы РВЛ в подсказку не попадают. */
    @Test
    void search_skipsArchivedExpiredFutureAndNonRvlPolicies() {
        savePolicy("П-000020", "Волков", "Иван", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));
        savePolicy("П-000021", "Волков", "Архив", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT), true);
        savePolicy("П-000022", "Волков", "Истёк", null, TODAY.minusYears(2), TODAY.minusDays(1), List.of(RVL_PROGRAM_EXT));
        savePolicy("П-000023", "Волков", "Будущий", null, TODAY.plusDays(1), null, List.of(RVL_PROGRAM_EXT));
        savePolicy("П-000024", "Волков", "Безрвл", null, TODAY.minusMonths(1), null, List.of(UNKNOWN_PROGRAM_EXT));

        MvcTestResult result = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}",
                CONTRACT_EXT, "волк").exchange();

        assertThat(result).bodyJson().extractingPath("$.items[*].first_name").asArray().containsExactly("Иван");
    }

    @Test
    void search_validatesQueryLimitAndContract() {
        MvcTestResult shortQuery = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}",
                CONTRACT_EXT, "и").exchange();
        assertThat(shortQuery).hasStatus(400);
        assertThat(shortQuery).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("query");

        MvcTestResult bigLimit = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}&limit=51",
                CONTRACT_EXT, "ив").exchange();
        assertThat(bigLimit).hasStatus(400);
        assertThat(bigLimit).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("limit");

        MvcTestResult noQuery = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}", CONTRACT_EXT).exchange();
        assertThat(noQuery).hasStatus(400);
        assertThat(noQuery).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("query");

        MvcTestResult unknownContract = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}",
                UUID.randomUUID(), "ив").exchange();
        assertThat(unknownContract).hasStatus(404);
        assertThat(unknownContract).bodyJson().extractingPath("$.error.code").isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");
    }

    @Test
    void search_limitCutsRankedList() {
        savePolicy("П-000030", "Соколов", "Иван", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));
        savePolicy("П-000031", "Соколова", "Ирина", null, TODAY.minusMonths(1), null, List.of(RVL_PROGRAM_EXT));

        MvcTestResult result = mvc.get().uri(BASE + "/policies/search?contract_external_id={c}&query={q}&limit=1",
                CONTRACT_EXT, "соко").exchange();

        assertThat(result).bodyJson().extractingPath("$.items").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].last_name").isEqualTo("Соколов");
    }

    private Policy savePolicy(String number, String lastName, String firstName, @Nullable String middleName,
            LocalDate beginDate, @Nullable LocalDate endDate, List<UUID> programExternalIds) {
        return savePolicy(number, lastName, firstName, middleName, beginDate, endDate, programExternalIds, false);
    }

    private Policy savePolicy(String number, String lastName, String firstName, @Nullable String middleName,
            LocalDate beginDate, @Nullable LocalDate endDate, List<UUID> programExternalIds, boolean archived) {
        var details = new Policy.Details(CONTRACT_EXT, number, lastName, firstName, middleName,
                LocalDate.of(1984, 3, 11), "Мужской", "petrov@example.test", beginDate, endDate, true, archived);
        List<PolicyProgram.Row> programs = programExternalIds.stream()
                .map(program -> new PolicyProgram.Row(program, beginDate, endDate, null, null, null, null,
                        new BigDecimal("150000.00"), true))
                .toList();
        List<PolicyServiceLine.Row> services = programExternalIds.stream()
                .map(program -> new PolicyServiceLine.Row(program, 1, new BigDecimal("20.00"), beginDate, endDate, true))
                .toList();
        return policyRepository.save(Policy.create(UUID.randomUUID(),
                new Policy.Snapshot(details, programs, services), NOW));
    }
}
