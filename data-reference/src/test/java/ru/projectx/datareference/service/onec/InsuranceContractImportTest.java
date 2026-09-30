package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.InsuranceContractMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-2: договоры страхования. Поток несёт договоры всех продуктов страховщика,
 * поэтому проверяется прежде всего фильтр — какие сообщения вообще доходят до таблицы.
 *
 * <p>Даты здесь третьего вида: момент <b>без</b> смещения ({@code 2026-05-07T00:00:00}).
 * До этого потока такой формы не встречалось, и разбирать её никто не умел.
 */
@IntegrationTest
class InsuranceContractImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_contracts-changes";
    private static final String DMS = "Личное страхование";
    private static final UUID CONTRACT_ID = UUID.fromString("75af8e16-78d0-3b1f-8405-349aae9b01e8");
    private static final UUID INSURER_ID = UUID.fromString("5e595002-41bd-4904-8b4e-f7690896baa0");
    private static final UUID INSURED_ID = UUID.fromString("5e595002-41bd-4904-8b4e-f7690896bbbb");

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private InsuranceContractRepository repository;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void dmsContract_isStored() {
        importOne(contract(DMS, insurerParty()));

        InsuranceContract stored = repository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.number()).isEqualTo("0646480513");
        assertThat(stored.externalInsurerId()).isEqualTo(INSURER_ID);
        assertThat(stored.isArchive()).isFalse();
    }

    /**
     * Даты приходят моментом без смещения, и календарный день берётся как написан —
     * конец дня 23:59:59 остаётся тем же днём, а не переезжает на следующий.
     */
    @Test
    void datesWithoutOffset_keepTheirCalendarDay() {
        importOne(contract(DMS, insurerParty()));

        InsuranceContract stored = repository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.beginDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(stored.endDate()).isEqualTo(LocalDate.of(2027, 5, 6));
    }

    /** Поток несёт все продукты страховщика; чужие до таблицы доходить не должны. */
    @Test
    void contractOfAnotherProduct_isSkipped() {
        ImportResult result = importOne(contract("Обязательное страхование", insurerParty()));

        assertThat(result)
                .isInstanceOfSatisfying(ImportResult.Skipped.class,
                        skipped -> assertThat(skipped.reason()).contains("не ДМС"));
        assertThat(repository.count()).isZero();
    }

    /**
     * Сторон в договоре несколько, и брать первую попавшуюся нельзя: застрахованный
     * и страхователь — разные лица.
     */
    @Test
    void insurer_isPickedByRoleAmongSeveralParties() {
        importOne(contract(DMS, party(INSURED_ID, "Застрахованный") + "," + insurerParty()));

        assertThat(repository.findByExternalId(CONTRACT_ID).orElseThrow().externalInsurerId())
                .isEqualTo(INSURER_ID);
    }

    /** Страхователем может быть физлицо, которого в справочнике нет: договор всё равно наш. */
    @Test
    void contractWithoutInsurerRole_isStoredWithoutInsurer() {
        importOne(contract(DMS, party(INSURED_ID, "Застрахованный")));

        assertThat(repository.findByExternalId(CONTRACT_ID).orElseThrow().externalInsurerId()).isNull();
    }

    /** {@code notActive} у этого потока — то же, чем {@code deleteMark} у остальных. */
    @Test
    void notActiveContract_isArchived() {
        importOne(contract(DMS, insurerParty()).replace("\"notActive\":false", "\"notActive\":true"));

        assertThat(repository.findByExternalId(CONTRACT_ID).orElseThrow().isArchive()).isTrue();
    }

    /**
     * Тип продукта не задан — применять нечего: отличить ДМС от чужого продукта нечем,
     * а сохранить всё подряд значит завести у себя договоры еОСАГО.
     *
     * <p>Маппер собирается здесь руками, а не берётся из контекста: проверяется поведение
     * при <i>другой</i> конфигурации, а менять её у общего контекста ради одного теста
     * значило бы поднимать второй контекст на весь прогон.
     */
    @Test
    void productTypeNotConfigured_isSkipped() {
        InsuranceContractMapper withoutDms = new InsuranceContractMapper(
                repository,
                new OneCContractProperties(null, null, null, "Адрес", List.of()),
                clock);

        ImportResult result = withoutDms.apply(dataOf(contract(DMS, insurerParty())));

        assertThat(result)
                .isInstanceOfSatisfying(ImportResult.Skipped.class,
                        skipped -> assertThat(skipped.reason()).contains("dms-product-type"));
    }

    private ImportResult importOne(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.INSURANCE_CONTRACTS, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private ObjectNode dataOf(String data) {
        return (ObjectNode) json.readTree(data);
    }

    /** Пример из ТЗ-2, сокращённый до полей маппинга. */
    private static String contract(String productType, String parties) {
        return """
                {"contractId":"%s","number":"0646480513","productType":"%s",\
                "productCode":"300000057","dateBeginISO":"2026-05-07T00:00:00",\
                "dateEndISO":"2027-05-06T23:59:59","notActive":false,\
                "contragentList":[%s]}"""
                .formatted(CONTRACT_ID, productType, parties);
    }

    private static String insurerParty() {
        return party(INSURER_ID, "Страхователь");
    }

    private static String party(UUID guid, String roleId) {
        return """
                {"rolesList":[{"roleName":"%s","roleId":"%s"}],\
                "contragent":{"type":"Юридическое лицо","fullName":"АО «Работодатель»","guid":"%s"}}"""
                .formatted(roleId, roleId, guid);
    }
}
