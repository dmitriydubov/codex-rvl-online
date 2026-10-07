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
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.Policy;
import ru.projectx.datareference.entity.PolicyProgram;
import ru.projectx.datareference.entity.PolicyServiceLine;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.repository.AppealTypeRepository;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Каталог ЛПУ по договору страхования, карточка ЛПУ и его услуги с ценами — через
 * настоящий стек MVC поверх настоящей базы. Часы остановлены на 14.03.2026: «сегодня»
 * для правила дат — этот день.
 *
 * <p>Виды обращения РВЛ в справочнике записаны не так, как в конфигурации, — с лишними
 * пробелами и в другом регистре: так проверяется, что сравнение их переживает.
 */
@IntegrationTest
class LpuCatalogApiTest {

    private static final String BASE = InternalApi.BASE_PATH;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final UUID CONTRACT_EXT = UUID.fromString("6b1c0000-0000-4000-8000-0000000000c1");
    private static final UUID PROGRAM_EXT = UUID.fromString("6b1c0000-0000-4000-8000-0000000000b1");
    private static final UUID RVL_SKL_EXT = UUID.fromString("6b1c0000-0000-4000-8000-0000000000a1");
    private static final UUID RVL_RUSSIA_EXT = UUID.fromString("6b1c0000-0000-4000-8000-0000000000a2");
    private static final UUID AMBULATORY_EXT = UUID.fromString("6b1c0000-0000-4000-8000-0000000000a3");
    private static final BigDecimal NO_COPAYMENT = new BigDecimal("0.00");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private AppealTypeRepository appealTypeRepository;

    @Autowired
    private LpuRepository lpuRepository;

    @Autowired
    private LpuContractRepository lpuContractRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private InsuranceContractRepository insuranceContractRepository;

    @Autowired
    private ContragentServiceRepository contragentServiceRepository;

    @Autowired
    private PriceListItemRepository priceListItemRepository;

    @BeforeEach
    void referenceData() {
        databaseCleaner.clear();
        saveAppealType(RVL_SKL_EXT, "  рвл и скл ");
        saveAppealType(RVL_RUSSIA_EXT, "РВЛ В РОССИИ");
        saveAppealType(AMBULATORY_EXT, "Амбулаторно-поликлиническая помощь");
        insuranceContractRepository.save(InsuranceContract.create(CONTRACT_EXT,
                new InsuranceContract.Details("ДМС-2026/001", TODAY.minusYears(1), null, null, false), NOW));
    }

    @Test
    void catalog_listsLpuOfRvlProgramsByName_skipsAmbiguous_dropsTheRest() {
        Lpu mainOnly = saveLpu("Сосновый бор", false);
        saveContract(mainOnly, "M-1", null, false, TODAY.minusYears(1), null);
        Lpu withSupplement = saveLpu("Ай-Петри", false);
        LpuContract main = saveContract(withSupplement, "M-2", null, false, TODAY.minusYears(1), null);
        saveContract(withSupplement, "D-2", main.externalId(), false, TODAY.minusMonths(1), null);
        Lpu ambiguous = saveLpu("Юбилейный", false);
        saveContract(ambiguous, "M-3a", null, false, TODAY.minusYears(1), null);
        saveContract(ambiguous, "M-3b", null, false, TODAY.minusMonths(6), null);
        Lpu ambulatoryOnly = saveLpu("Берёзка", false);
        saveContract(ambulatoryOnly, "M-4", null, false, TODAY.minusYears(1), null);
        Lpu withoutContract = saveLpu("Заря", false);
        Lpu archived = saveLpu("Волна", true);
        saveContract(archived, "M-6", null, false, TODAY.minusYears(1), null);
        saveProgram(List.of(row(mainOnly, RVL_SKL_EXT), row(withSupplement, RVL_RUSSIA_EXT),
                row(ambiguous, RVL_SKL_EXT), row(ambulatoryOnly, AMBULATORY_EXT),
                row(withoutContract, RVL_SKL_EXT), row(archived, RVL_SKL_EXT)));
        saveActivePolicy(PROGRAM_EXT);

        MvcTestResult result = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", CONTRACT_EXT).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].name").asArray()
                .containsExactly("Ай-Петри", "Сосновый бор");
        assertThat(result).bodyJson().extractingPath("$.items[1].id").isEqualTo(mainOnly.id().toString());
        assertThat(result).bodyJson().extractingPath("$.items[1].external_id").isEqualTo(mainOnly.externalId().toString());
        assertThat(result).bodyJson().extractingPath("$.items[1].region").isEqualTo("Московская область");
        assertThat(result).bodyJson().extractingPath("$.items[1].inn").isEqualTo("7701234567");
        assertThat(result).bodyJson().extractingPath("$.items[1].kpp").isEqualTo("770101001");
        assertThat(result).bodyJson().extractingPath("$.skipped").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.skipped[0].lpu_id").isEqualTo(ambiguous.id().toString());
        assertThat(result).bodyJson().extractingPath("$.skipped[0].reason").isEqualTo("AMBIGUOUS_PRICE_CONTRACT");
    }

    /** Полис с истёкшим сроком, с условием без доступности или по неизвестной программе учреждений не даёт. */
    @Test
    void catalog_isEmptyWithoutActiveRvlPolicies_andNeedsAnActiveContract() {
        Lpu lpu = saveLpu("Сосновый бор", false);
        saveContract(lpu, "M-1", null, false, TODAY.minusYears(1), null);
        saveProgram(List.of(row(lpu, RVL_SKL_EXT)));
        savePolicy(PROGRAM_EXT, TODAY.minusYears(2), TODAY.minusDays(1), true);
        savePolicy(PROGRAM_EXT, TODAY.minusMonths(1), null, false);
        savePolicy(UUID.randomUUID(), TODAY.minusMonths(1), null, true);

        MvcTestResult empty = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", CONTRACT_EXT).exchange();
        assertThat(empty).hasStatusOk();
        assertThat(empty).bodyJson().extractingPath("$.items").asArray().isEmpty();
        assertThat(empty).bodyJson().extractingPath("$.skipped").asArray().isEmpty();

        MvcTestResult unknown = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", UUID.randomUUID()).exchange();
        assertThat(unknown).hasStatus(404);
        assertThat(unknown).bodyJson().extractingPath("$.error.code").isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");

        UUID archivedContract = UUID.randomUUID();
        insuranceContractRepository.save(InsuranceContract.create(archivedContract,
                new InsuranceContract.Details("ДМС-2025/009", TODAY.minusYears(2), null, null, true), NOW));
        MvcTestResult archived = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", archivedContract).exchange();
        assertThat(archived).hasStatus(404);

        MvcTestResult noKey = mvc.get().uri(BASE + "/lpu").exchange();
        assertThat(noKey).hasStatus(400);
        assertThat(noKey).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("contract_external_id");
    }

    @Test
    void catalog_contractOutOfItsTerm_isNotFound_likeArchived() {
        UUID expired = UUID.randomUUID();
        insuranceContractRepository.save(InsuranceContract.create(expired,
                new InsuranceContract.Details("ДМС-2025/010", TODAY.minusYears(1), TODAY.minusDays(1), null, false), NOW));
        UUID notStarted = UUID.randomUUID();
        insuranceContractRepository.save(InsuranceContract.create(notStarted,
                new InsuranceContract.Details("ДМС-2026/011", TODAY.plusDays(1), null, null, false), NOW));

        for (UUID contract : List.of(expired, notStarted)) {
            MvcTestResult result = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", contract).exchange();
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");
        }
    }

    /** Строка условий обслуживания, которая начнётся завтра, учреждений не даёт. */
    @Test
    void catalog_serviceLineStartingTomorrow_givesNoLpu() {
        lpuOfProgramWithServiceLineFrom(TODAY.plusDays(1));

        MvcTestResult result = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", CONTRACT_EXT).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    /** Первый день срока строки условий уже входит в срок. */
    @Test
    void catalog_serviceLineStartingToday_givesLpu() {
        lpuOfProgramWithServiceLineFrom(TODAY);

        MvcTestResult result = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", CONTRACT_EXT).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].name").asArray().containsExactly("Сосновый бор");
    }

    /** Строка условий без даты начала срок с этой стороны не ограничивает. */
    @Test
    void catalog_serviceLineWithoutBeginDate_givesLpu() {
        lpuOfProgramWithServiceLineFrom(null);

        MvcTestResult result = mvc.get().uri(BASE + "/lpu?contract_external_id={c}", CONTRACT_EXT).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].name").asArray().containsExactly("Сосновый бор");
    }

    @Test
    void card_returnsPriceContract_supplementWithOwnPricesBeatsMain() {
        Lpu lpu = saveLpu("Ай-Петри", false);
        LpuContract main = saveContract(lpu, "2724RB152", null, false, TODAY.minusYears(1), TODAY.plusMonths(9));
        LpuContract supplement = saveContract(lpu, "2724RB152/Д1", main.externalId(), false,
                TODAY.minusMonths(1), TODAY.plusMonths(2));
        saveContract(lpu, "2724RB152/Д2", main.externalId(), true, TODAY.minusMonths(1), null);

        MvcTestResult result = mvc.get().uri(BASE + "/lpu/{id}", lpu.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(lpu.id().toString());
        assertThat(result).bodyJson().extractingPath("$.external_id").isEqualTo(lpu.externalId().toString());
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Ай-Петри");
        assertThat(result).bodyJson().extractingPath("$.address").isEqualTo("г. Звенигород, ул. Лесная, 1");
        assertThat(result).bodyJson().extractingPath("$.price_contract.id").isEqualTo(supplement.id().toString());
        assertThat(result).bodyJson().extractingPath("$.price_contract.external_id")
                .isEqualTo(supplement.externalId().toString());
        assertThat(result).bodyJson().extractingPath("$.price_contract.number").isEqualTo("2724RB152/Д1");
        assertThat(result).bodyJson().extractingPath("$.price_contract.begin_date")
                .isEqualTo(TODAY.minusMonths(1).toString());
        assertThat(result).bodyJson().extractingPath("$.price_contract.end_date").isEqualTo(TODAY.plusMonths(2).toString());
        assertThat(result).bodyJson().extractingPath("$.price_contract.uploaded_to_sob").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.price_contract.kind").isEqualTo("DOPS");
    }

    /** Допсоглашение, берущее цены с главного, кандидатом не считается; договор будущего года — ещё нет. */
    @Test
    void card_fallsBackToMain_ignoringSupplementOnMainPricesAndFutureContracts() {
        Lpu lpu = saveLpu("Сосновый бор", false);
        LpuContract main = saveContract(lpu, "M-1", null, false, TODAY.minusYears(1), TODAY.plusMonths(9));
        saveContract(lpu, "M-1/Д1", main.externalId(), true, TODAY.minusMonths(1), null);
        saveContract(lpu, "M-2027", null, false, TODAY.plusMonths(10), null);

        MvcTestResult result = mvc.get().uri(BASE + "/lpu/{id}", lpu.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.price_contract.id").isEqualTo(main.id().toString());
        assertThat(result).bodyJson().extractingPath("$.price_contract.kind").isEqualTo("MAIN");
    }

    @Test
    void card_reportsAmbiguousAndMissingPriceContract_andUnknownOrArchivedLpu() {
        Lpu ambiguous = saveLpu("Юбилейный", false);
        LpuContract main = saveContract(ambiguous, "M-3", null, false, TODAY.minusYears(1), null);
        LpuContract first = saveContract(ambiguous, "M-3/Д1", main.externalId(), false, TODAY.minusMonths(2), null);
        LpuContract second = saveContract(ambiguous, "M-3/Д2", main.externalId(), false, TODAY.minusMonths(1), null);
        Lpu expired = saveLpu("Заря", false);
        saveContract(expired, "M-5", null, false, TODAY.minusYears(2), TODAY.minusDays(1));
        Lpu archived = saveLpu("Волна", true);
        saveContract(archived, "M-6", null, false, TODAY.minusYears(1), null);

        MvcTestResult conflict = mvc.get().uri(BASE + "/lpu/{id}", ambiguous.id()).exchange();
        assertThat(conflict).hasStatus(409);
        assertThat(conflict).bodyJson().extractingPath("$.error.code").isEqualTo("AMBIGUOUS_PRICE_CONTRACT");
        assertThat(conflict).bodyJson().extractingPath("$.error.details[*].lpu_contract_id").asArray()
                .containsExactlyInAnyOrder(first.id().toString(), second.id().toString());

        MvcTestResult none = mvc.get().uri(BASE + "/lpu/{id}", expired.id()).exchange();
        assertThat(none).hasStatus(422);
        assertThat(none).bodyJson().extractingPath("$.error.code").isEqualTo("NO_ACTIVE_PRICE_CONTRACT");
        assertThat(none).bodyJson().extractingPath("$.error.details").asArray().isEmpty();

        MvcTestResult gone = mvc.get().uri(BASE + "/lpu/{id}", archived.id()).exchange();
        assertThat(gone).hasStatus(404);
        assertThat(gone).bodyJson().extractingPath("$.error.code").isEqualTo("LPU_NOT_FOUND");

        MvcTestResult unknown = mvc.get().uri(BASE + "/lpu/{id}", UUID.randomUUID()).exchange();
        assertThat(unknown).hasStatus(404);
    }

    @Test
    void services_filtersByRvlAppealTypes_sortsByName_andOmitsPricesUnlessAsked() {
        Lpu lpu = saveLpu("Сосновый бор", false);
        LpuContract main = saveContract(lpu, "M-1", null, false, TODAY.minusYears(1), null);
        LpuContract other = saveContract(lpu, "M-1/Д1", main.externalId(), true, TODAY.minusMonths(1), null);
        ContragentService twoBed = saveService(main, "Путёвка 2-местная", "41.178.178", RVL_SKL_EXT, false);
        saveService(main, "Путёвка 1-местная", "41.178.177", RVL_RUSSIA_EXT, false);
        saveService(main, "Массаж", "41.200.001", AMBULATORY_EXT, false);
        saveService(main, "Без вида обращения", "41.200.002", null, false);
        saveService(main, "Архивная путёвка", "41.178.100", RVL_SKL_EXT, true);
        saveService(other, "Путёвка с другого договора", "41.178.199", RVL_SKL_EXT, false);
        savePrice(twoBed, "12000.00", "11400.00", TODAY.minusMonths(1), TODAY.plusMonths(1));

        MvcTestResult result = mvc.get().uri(BASE + "/lpu/{id}/services", lpu.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lpu.id").isEqualTo(lpu.id().toString());
        assertThat(result).bodyJson().extractingPath("$.lpu.name").isEqualTo("Сосновый бор");
        assertThat(result).bodyJson().extractingPath("$.price_contract.id").isEqualTo(main.id().toString());
        assertThat(result).bodyJson().extractingPath("$.price_contract.kind").isEqualTo("MAIN");
        assertThat(result).bodyJson().extractingPath("$.services[*].name").asArray()
                .containsExactly("Путёвка 1-местная", "Путёвка 2-местная");
        assertThat(result).bodyJson().extractingPath("$.services[1].id").isEqualTo(twoBed.id().toString());
        assertThat(result).bodyJson().extractingPath("$.services[1].external_id").isEqualTo(twoBed.externalId().toString());
        assertThat(result).bodyJson().extractingPath("$.services[1].code").isEqualTo("41.178.178");
        assertThat(result).bodyJson().extractingPath("$.services[1].appeal_type.external_id").isEqualTo(RVL_SKL_EXT.toString());
        assertThat(result).bodyJson().extractingPath("$.services[1].appeal_type.name").isEqualTo("  рвл и скл ");
        assertThat(result).bodyJson().extractingPath("$.services[1]").asMap().doesNotContainKey("prices");
    }

    /** Прошедший период не отдаётся, будущий — да; услуга без цен остаётся с пустым массивом. */
    @Test
    void services_withPrices_returnsCurrentAndFuturePeriodsByValidFrom() {
        Lpu lpu = saveLpu("Сосновый бор", false);
        LpuContract main = saveContract(lpu, "M-1", null, false, TODAY.minusYears(1), null);
        ContragentService priced = saveService(main, "Путёвка 2-местная", "41.178.178", RVL_SKL_EXT, false);
        ContragentService unpriced = saveService(main, "Путёвка люкс", "41.178.179", RVL_RUSSIA_EXT, false);
        savePrice(priced, "9000.00", null, TODAY.minusYears(1), TODAY.minusDays(1));
        savePrice(priced, "13000.00", null, TODAY.plusMonths(2), null);
        savePrice(priced, "12000.00", "11400.00", TODAY.minusMonths(1), TODAY.plusMonths(2).minusDays(1));

        MvcTestResult result = mvc.get().uri(BASE + "/lpu/{id}/services?need_prices=true", lpu.id()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.services[0].id").isEqualTo(priced.id().toString());
        assertThat(result).bodyJson().extractingPath("$.services[0].prices").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.services[0].prices[0].price").isEqualTo(12000.00);
        assertThat(result).bodyJson().extractingPath("$.services[0].prices[0].price_with_discount").isEqualTo(11400.00);
        assertThat(result).bodyJson().extractingPath("$.services[0].prices[0].valid_from")
                .isEqualTo(TODAY.minusMonths(1).toString());
        assertThat(result).bodyJson().extractingPath("$.services[0].prices[0].service_code").isEqualTo("26.23.006/82");
        assertThat(result).bodyJson().extractingPath("$.services[0].prices[1].price").isEqualTo(13000.00);
        assertThat(result).bodyJson().extractingPath("$.services[0].prices[1].valid_to").isNull();
        assertThat(result).bodyJson().extractingPath("$.services[1].id").isEqualTo(unpriced.id().toString());
        assertThat(result).bodyJson().extractingPath("$.services[1].prices").asArray().isEmpty();
    }

    @Test
    void services_needAPriceContractToo() {
        Lpu lpu = saveLpu("Заря", false);

        MvcTestResult result = mvc.get().uri(BASE + "/lpu/{id}/services?need_prices=true", lpu.id()).exchange();

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("NO_ACTIVE_PRICE_CONTRACT");
    }

    private void saveAppealType(UUID externalId, String name) {
        appealTypeRepository.save(AppealType.create(externalId,
                new AppealType.Details(null, null, name, null, null, null, true, false, false), NOW));
    }

    private Lpu saveLpu(String name, boolean archived) {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details(name, "АО «" + name + "»", "7701234567", "770101001", null, null, archived), NOW);
        lpu.applyContacts("г. Звенигород, ул. Лесная, 1", "Московская область", NOW);
        return lpuRepository.save(lpu);
    }

    private LpuContract saveContract(Lpu lpu, String number, @Nullable UUID mainContractExternalId,
            boolean usePricesMainContract, LocalDate beginDate, @Nullable LocalDate endDate) {
        return lpuContractRepository.save(LpuContract.create(UUID.randomUUID(), lpu,
                new LpuContract.Details(number, "Договор " + number, mainContractExternalId, usePricesMainContract,
                        true, beginDate, endDate, false), NOW));
    }

    private void saveProgram(List<ProgramLpu.Row> rows) {
        Program program = Program.create(PROGRAM_EXT,
                new Program.Details(null, null, "РВЛ и СКЛ, стандарт", "РВЛ", false), NOW);
        program.replaceAvailableLpu(rows, NOW);
        programRepository.save(program);
    }

    private static ProgramLpu.Row row(Lpu lpu, @Nullable UUID appealTypeExternalId) {
        return new ProgramLpu.Row(lpu, appealTypeExternalId, TODAY.minusYears(1), null, 1, true, NO_COPAYMENT,
                ProgramLpu.CoverageType.R);
    }

    private void saveActivePolicy(UUID programExternalId) {
        savePolicy(programExternalId, TODAY.minusMonths(1), null, true);
    }

    private void savePolicy(UUID programExternalId, LocalDate beginDate, @Nullable LocalDate endDate,
            boolean serviceAvailable) {
        var details = new Policy.Details(CONTRACT_EXT, "0001-" + UUID.randomUUID().toString().substring(0, 6),
                "Иванов", "Иван", null, LocalDate.of(1985, 3, 14), "Мужской", null, beginDate, endDate, true, false);
        var program = new PolicyProgram.Row(programExternalId, beginDate, endDate, null, null, null, null,
                new BigDecimal("150000.00"), true);
        var service = new PolicyServiceLine.Row(programExternalId, 1, NO_COPAYMENT, beginDate, endDate,
                serviceAvailable);
        policyRepository.save(Policy.create(UUID.randomUUID(),
                new Policy.Snapshot(details, List.of(program), List.of(service)), NOW));
    }

    /** Учреждение с действующим договором в перечне программы и действующий полис, чья строка условий начинается с {@code lineBegin}. */
    private void lpuOfProgramWithServiceLineFrom(@Nullable LocalDate lineBegin) {
        Lpu lpu = saveLpu("Сосновый бор", false);
        saveContract(lpu, "M-1", null, false, TODAY.minusYears(1), null);
        saveProgram(List.of(row(lpu, RVL_SKL_EXT)));
        savePolicyWithServiceLineFrom(lineBegin);
    }

    /** Действующий полис, у строки условий которого своя дата начала, а окончания нет. */
    private void savePolicyWithServiceLineFrom(@Nullable LocalDate lineBegin) {
        LocalDate begin = TODAY.minusMonths(1);
        var details = new Policy.Details(CONTRACT_EXT, "0002-" + UUID.randomUUID().toString().substring(0, 6),
                "Петров", "Пётр", null, LocalDate.of(1985, 3, 14), "Мужской", null, begin, null, true, false);
        var program = new PolicyProgram.Row(PROGRAM_EXT, begin, null, null, null, null, null,
                new BigDecimal("150000.00"), true);
        var service = new PolicyServiceLine.Row(PROGRAM_EXT, 1, NO_COPAYMENT, lineBegin, null, true);
        policyRepository.save(Policy.create(UUID.randomUUID(),
                new Policy.Snapshot(details, List.of(program), List.of(service)), NOW));
    }

    private ContragentService saveService(LpuContract contract, String name, String code,
            @Nullable UUID appealTypeExternalId, boolean archived) {
        return contragentServiceRepository.save(ContragentService.create(UUID.randomUUID(), contract,
                new ContragentService.Details(appealTypeExternalId, code, name, archived), NOW));
    }

    private void savePrice(ContragentService service, String price, @Nullable String priceWithDiscount,
            LocalDate validFrom, @Nullable LocalDate validTo) {
        priceListItemRepository.save(PriceListItem.of(service,
                new PriceListItem.Row("Проживание, 2-местный номер", "26.23.006/82", null, new BigDecimal(price),
                        priceWithDiscount == null ? null : new BigDecimal(priceWithDiscount), validFrom, validTo),
                NOW));
    }
}
