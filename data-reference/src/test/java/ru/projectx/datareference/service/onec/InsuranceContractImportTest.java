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
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.InsuranceContractMapper;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@IntegrationTest
class InsuranceContractImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_contracts-changes";
    private static final String DMS = "ЛичноеСтрахование";
    private static final String CONTRACT_NUMBER = "0000000001";
    private static final UUID CONTRACT_ID = UUID.fromString("7e570000-0000-4000-8000-0000000000c1");
    private static final UUID INSURER_ID = UUID.fromString("7e570000-0000-4000-8000-0000000000d1");
    private static final UUID INSURED_ID = UUID.fromString("7e570000-0000-4000-8000-0000000000d2");

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
        assertThat(stored.number()).isEqualTo(CONTRACT_NUMBER);
        assertThat(stored.externalInsurerId()).isEqualTo(INSURER_ID);
        assertThat(stored.isArchive()).isFalse();
    }

    @Test
    void datesWithoutOffset_keepTheirCalendarDay() {
        importOne(contract(DMS, insurerParty()));

        InsuranceContract stored = repository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.beginDate()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(stored.endDate()).isEqualTo(LocalDate.of(2027, 5, 6));
    }

    @Test
    void contractOfAnotherProduct_isSkipped() {
        ImportOutcome result = importOne(contract("Обязательное страхование", insurerParty()));

        assertThat(result)
                .isInstanceOfSatisfying(ImportOutcome.Skipped.class,
                        skipped -> assertThat(skipped.reason()).contains("не ДМС"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void insurer_isPickedByRoleAmongSeveralParties() {
        importOne(contract(DMS, party(INSURED_ID, "Застрахованный") + "," + insurerParty()));

        assertThat(repository.findByExternalId(CONTRACT_ID).orElseThrow().externalInsurerId())
                .isEqualTo(INSURER_ID);
    }

    @Test
    void contractWithoutInsurerRole_isStoredWithoutInsurer() {
        importOne(contract(DMS, party(INSURED_ID, "Застрахованный")));

        assertThat(repository.findByExternalId(CONTRACT_ID).orElseThrow().externalInsurerId()).isNull();
    }

    @Test
    void notActiveContract_isArchived() {
        importOne(contract(DMS, insurerParty()).replace("\"notActive\":false", "\"notActive\":true"));

        assertThat(repository.findByExternalId(CONTRACT_ID).orElseThrow().isArchive()).isTrue();
    }

    @Test
    void productTypeNotConfigured_isSkipped() {
        InsuranceContractMapper withoutDms = new InsuranceContractMapper(
                repository,
                new OneCContractProperties(null, null, List.of(), "Страхователь", "Адрес", List.of()),
                clock);

        ImportResult result = withoutDms.apply(dataOf(contract(DMS, insurerParty())));

        assertThat(result)
                .isInstanceOfSatisfying(ImportResult.Skipped.class,
                        skipped -> assertThat(skipped.reason()).contains("dms-product-type"));
    }

    private ImportOutcome importOne(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.INSURANCE_CONTRACTS, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private ObjectNode dataOf(String data) {
        return (ObjectNode) json.readTree(data);
    }

    private static String contract(String productType, String parties) {
        return """
                {"contractId":"%s","number":"%s","productType":"%s",\
                "productCode":"000000001","dateBeginISO":"2026-05-07T00:00:00",\
                "dateEndISO":"2027-05-06T23:59:59","notActive":false,\
                "contragentList":[%s]}"""
                .formatted(CONTRACT_ID, CONTRACT_NUMBER, productType, parties);
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
