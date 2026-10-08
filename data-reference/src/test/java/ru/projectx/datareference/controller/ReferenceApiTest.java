package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Insurer;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Проверяет то, на что опираются соседние сервисы при чтении программ и договоров страхования: поля в snake_case,
 * оба идентификатора в ответе, формат ошибки и сквозной {@code X-Request-Id}. Запросы идут через настоящий стек MVC
 * поверх настоящей базы; названия, номера договоров и реквизиты страхователя вымышленные.
 */
@IntegrationTest
class ReferenceApiTest {

    private static final String BASE = InternalApi.BASE_PATH;
    private static final UUID PROGRAM_EXT = UUID.fromString("7d3a9e60-0000-4000-8000-0000000000a1");
    private static final UUID LPU_EXT = UUID.fromString("7d3a9e60-0000-4000-8000-0000000000a2");
    private static final UUID POLICY_EXT = UUID.fromString("7d3a9e60-0000-4000-8000-0000000000a3");
    private static final UUID CONTRACT_EXT = UUID.fromString("7d3a9e60-0000-4000-8000-0000000000a4");
    private static final UUID INSURER_EXT = UUID.fromString("7d3a9e60-0000-4000-8000-0000000000a5");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private LpuRepository lpuRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private InsuranceContractRepository insuranceContractRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void program_returnsBothIdsNameAndServices() {
        Lpu lpu = lpuRepository.save(Lpu.create(LPU_EXT,
                new Lpu.Details("Санаторий «Тестовый»", null, null, null, null, null, false), NOW));
        Program program = Program.create(PROGRAM_EXT,
                new Program.Details(null, null, "Санаторно-курортное лечение", "СКЛ", false), NOW);
        program.replaceAvailableLpu(List.of(new ProgramLpu.Row(lpu, null, LocalDate.of(2026, 1, 1), null,
                1, true, new BigDecimal("10.00"), ProgramLpu.CoverageType.R)), NOW);
        programRepository.save(program);

        MvcTestResult result = mvc.get().uri(BASE + "/programs/{id}", program.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(program.id().toString());
        assertThat(result).bodyJson().extractingPath("$.external_id").isEqualTo(PROGRAM_EXT.toString());
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Санаторно-курортное лечение");
        assertThat(result).bodyJson().extractingPath("$.is_archive").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.services").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.services[0].lpu_external_id").isEqualTo(LPU_EXT.toString());
        assertThat(result).bodyJson().extractingPath("$.services[0].lpu_name").isEqualTo("Санаторий «Тестовый»");
        assertThat(result).bodyJson().extractingPath("$.services[0].coverage_type").isEqualTo("R");
        assertThat(result).bodyJson().extractingPath("$.services[0].percent_copayment").isEqualTo(10.00);
    }

    @Test
    void insuranceContract_returnsInsurerByLogicalLink() {
        insurerRepository.save(Insurer.create(INSURER_EXT,
                new Insurer.Details("АО «Работодатель»", "Акционерное общество «Работодатель»", "7701", "000000000", false),
                NOW));
        InsuranceContract contract = insuranceContractRepository.save(InsuranceContract.create(CONTRACT_EXT,
                new InsuranceContract.Details("0000000001", LocalDate.of(2026, 5, 7), LocalDate.of(2027, 5, 6),
                        INSURER_EXT, false), NOW));

        MvcTestResult result = mvc.get().uri(BASE + "/insurance-contracts/{id}", contract.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.number").isEqualTo("0000000001");
        assertThat(result).bodyJson().extractingPath("$.end_date").isEqualTo("2027-05-06");
        assertThat(result).bodyJson().extractingPath("$.insurer.external_id").isEqualTo(INSURER_EXT.toString());
        assertThat(result).bodyJson().extractingPath("$.insurer.name").isEqualTo("АО «Работодатель»");
        assertThat(result).bodyJson().extractingPath("$.insurer.full_name").isEqualTo("Акционерное общество «Работодатель»");
        assertThat(result).bodyJson().extractingPath("$.insurer.inn").isEqualTo("7701");
        assertThat(result).bodyJson().extractingPath("$.insurer.kpp").isEqualTo("000000000");
        assertThat(result).bodyJson().extractingPath("$.insurer.id").isNotNull();
    }

    @Test
    void insuranceContract_isFoundByExternalId_andRequiresAKey() {
        InsuranceContract contract = insuranceContractRepository.save(InsuranceContract.create(CONTRACT_EXT,
                new InsuranceContract.Details("0000000001", LocalDate.of(2026, 5, 7), null, null, false), NOW));

        MvcTestResult found = mvc.get().uri(BASE + "/insurance-contracts?external_id={ext}", CONTRACT_EXT).exchange();
        assertThat(found).hasStatusOk();
        assertThat(found).bodyJson().extractingPath("$.id").isEqualTo(contract.id().toString());

        MvcTestResult noKey = mvc.get().uri(BASE + "/insurance-contracts").exchange();
        assertThat(noKey).hasStatus(400);
        assertThat(noKey).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("external_id");
    }

    /** Страхователя-физического лица в справочнике нет, но договор всё равно отдаётся, только без страхователя. */
    @Test
    void insuranceContract_withoutInsurerInReference_hasNullInsurer() {
        InsuranceContract contract = insuranceContractRepository.save(InsuranceContract.create(CONTRACT_EXT,
                new InsuranceContract.Details("0000000001", LocalDate.of(2026, 5, 7), null, INSURER_EXT, false),
                NOW));

        MvcTestResult result = mvc.get().uri(BASE + "/insurance-contracts/{id}", contract.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.insurer").isNull();
    }

    @Test
    void unknownId_returns404InErrorFormatWithRequestId() {
        UUID missing = UUID.randomUUID();

        MvcTestResult result = mvc.get().uri(BASE + "/policies/{id}", missing)
                .header(RequestIdFilter.HEADER, "req-42")
                .exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).hasHeader(RequestIdFilter.HEADER, "req-42");
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("POLICY_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.error.message").asString().contains(missing.toString());
        assertThat(result).bodyJson().extractingPath("$.error.request_id").isEqualTo("req-42");
        assertThat(result).bodyJson().extractingPath("$.error.details").asArray().isEmpty();
    }

    @Test
    void malformedId_returns400WithFieldViolation() {
        MvcTestResult result = mvc.get().uri(BASE + "/programs/{id}", "not-a-uuid").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("id");
        assertThat(result).bodyJson().extractingPath("$.error.details[0].reason").isEqualTo("ожидается UUID");
    }

    @Test
    void unknownPath_returns404InErrorFormat() {
        MvcTestResult result = mvc.get().uri(BASE + "/nothing-here").exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("NOT_FOUND");
    }

    /** Без идентификатора запроса ошибку не найти в журнале, поэтому, если вызывающий его не прислал, его выдаёт сервис. */
    @Test
    void requestId_isIssuedWhenCallerSendsNone() {
        MvcTestResult result = mvc.get().uri(BASE + "/policies/{id}", UUID.randomUUID()).exchange();

        String issued = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(issued).isNotBlank();
        assertThat(result).bodyJson().extractingPath("$.error.request_id").isEqualTo(issued);
    }
}
