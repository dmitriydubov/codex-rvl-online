package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;
import static ru.projectx.datareference.support.OneCMessages.sanatorium;
import static ru.projectx.datareference.support.OneCMessages.sanatoriumWithoutRvl;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@Transactional
@IntegrationTest
class ContractAndServiceImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_contragents-contracts-changes";
    private static final UUID HEAD_OFFICE_ID = UUID.fromString("6c2f8d50-0000-4000-8000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("6c2f8d50-0000-4000-8000-000000000002");
    private static final UUID CONTRACT_ID = UUID.fromString("6c2f8d50-0000-4000-8000-00000000000a");
    private static final UUID MAIN_CONTRACT_ID = UUID.fromString("6c2f8d50-0000-4000-8000-0000000000aa");
    private static final UUID SERVICE_ID = UUID.fromString("6c2f8d50-0000-4000-8000-00000000000b");
    private static final UUID STRANGER_ID = UUID.fromString("6c2f8d50-0000-4000-8000-0000000000ff");

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private LpuRepository lpuRepository;

    @Autowired
    private LpuContractRepository contractRepository;

    @Autowired
    private ContragentServiceRepository serviceRepository;

    @BeforeEach
    void seedSanatoriums() {
        databaseCleaner.clear();
        importOne(OneCStream.CONTRAGENTS, sanatorium(HEAD_OFFICE_ID, "Санаторий «Заря»"));
        importOne(OneCStream.CONTRAGENTS, sanatorium(BRANCH_ID, "Санаторий «Заря», корпус 2"));
    }

    @Test
    void contractOfOurSanatorium_isStored() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false));

        LpuContract stored = contractRepository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.lpu().externalId()).isEqualTo(HEAD_OFFICE_ID);
        assertThat(stored.beginDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void contractOfBranch_belongsToBranchNotHeadOffice() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, BRANCH_ID, null, false));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().lpu().externalId())
                .isEqualTo(BRANCH_ID);
    }

    @Test
    void contractOfContragentWithoutRvl_isSkipped() {
        importOne(OneCStream.CONTRAGENTS, sanatoriumWithoutRvl(STRANGER_ID, "Санаторий «Чужой»"));

        ImportOutcome result = importOne(OneCStream.LPU_CONTRACTS, contract(STRANGER_ID, STRANGER_ID, null, false));

        assertThat(result).isInstanceOf(ImportOutcome.Skipped.class);
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void contractOfContragentNotYetReceived_waitsForIt() {
        UUID messageId = UUID.randomUUID();
        ImportOutcome result = importOne(messageId, OneCStream.LPU_CONTRACTS,
                contract(STRANGER_ID, STRANGER_ID, null, false));

        assertThat(result).isEqualTo(ImportOutcome.awaitingParent("ждёт родителя: контрагент " + STRANGER_ID));
        assertThat(inboundMessageRepository.findById(messageId).orElseThrow().outcome().status())
                .isEqualTo(InboundStatus.AWAITING_PARENT);
        assertThat(contractRepository.count()).isZero();
    }

    @Test
    void standaloneContract_hasNoMainContract() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false));

        LpuContract stored = contractRepository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.mainContractExternalId()).isNull();
        assertThat(stored.usePricesMainContract()).isFalse();
    }

    @Test
    void subordinateContract_keepsBothSourceFields() {
        importOne(OneCStream.LPU_CONTRACTS,
                contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, MAIN_CONTRACT_ID.toString(), true));

        LpuContract stored = contractRepository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.mainContractExternalId()).isEqualTo(MAIN_CONTRACT_ID);
        assertThat(stored.usePricesMainContract()).isTrue();
    }

    @Test
    void unknownMainContract_isStoredAnyway() {
        importOne(OneCStream.LPU_CONTRACTS,
                contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, MAIN_CONTRACT_ID.toString(), false));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().mainContractExternalId())
                .isEqualTo(MAIN_CONTRACT_ID);
    }

    @Test
    void sobExportProperty_isStored() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false, true));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().uploadedToSob()).isTrue();
    }

    @Test
    void contractWithoutSobProperty_isNotUploadedToSob() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().uploadedToSob()).isFalse();
    }

    @Test
    void serviceOfOurContract_isStored() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false));

        importOne(OneCStream.CONTRAGENT_SERVICES, service(CONTRACT_ID));

        ContragentService stored = serviceRepository.findByExternalId(SERVICE_ID).orElseThrow();
        assertThat(stored.name()).isEqualTo("Путёвка, стандарт");
        assertThat(stored.contract().externalId()).isEqualTo(CONTRACT_ID);
    }

    @Test
    void serviceArrivingBeforeItsContract_waitsForIt() {
        ImportOutcome result = importOne(OneCStream.CONTRAGENT_SERVICES, service(CONTRACT_ID));

        assertThat(result).isEqualTo(ImportOutcome.awaitingParent("ждёт родителя: договор ЛПУ " + CONTRACT_ID));
        assertThat(serviceRepository.count()).isZero();
    }

    private ImportOutcome importOne(OneCStream stream, String data) {
        return importOne(UUID.randomUUID(), stream, data);
    }

    private ImportOutcome importOne(UUID messageId, OneCStream stream, String data) {
        inboundMessageService.store(messageId, stream, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private static String contract(UUID ownerId, UUID branchId, String mainContractId, boolean useHeadOfficePrices) {
        return contract(ownerId, branchId, mainContractId, useHeadOfficePrices, null);
    }

    private static String contract(
            UUID ownerId, UUID branchId, String mainContractId, boolean useHeadOfficePrices,
            @Nullable Boolean sobExport) {

        return """
                {"id":"%s","ownerId":"%s","contragentBranchId":"%s","mainContractId":"%s",\
                "usePricesMainAgreement":%b,"number":"Д-1","name":"Договор на санаторное лечение",\
                "beginDate":"2024-01-01T00:00:00+03:00","endDate":"3000-01-01"%s}"""
                .formatted(CONTRACT_ID, ownerId, branchId,
                        mainContractId == null ? "00000000-0000-0000-0000-000000000000" : mainContractId,
                        useHeadOfficePrices,
                        sobExport == null ? "" : sobExportProperty(sobExport));
    }

    private static String sobExportProperty(boolean value) {
        return """
                ,"extProperties":[{"propertyName":"Выгружать прейскуранты в систему бронирования",\
                "value":{"value":%b}}]""".formatted(value);
    }

    private static String service(UUID contractId) {
        return """
                {"id":"%s","ownerId":"%s","code":"SKL-01","name":"Путёвка, стандарт"}"""
                .formatted(SERVICE_ID, contractId);
    }
}
