package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;
import static ru.projectx.datareference.support.OneCMessages.sanatorium;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * ТЗ-5 и ТЗ-10 вместе: договор с санаторием и услуги по нему — звенья одной цепочки,
 * и обрыв любого из них означает, что до цен дело не дойдёт.
 *
 * <p>{@code @Transactional} здесь не ради отката, а ради открытой сессии: проверки
 * читают ленивые связи «договор → санаторий» и «услуга → договор», а
 * {@code open-in-view} у сервиса выключен — вне транзакции они не догрузятся.
 * В бою эти связи читаются внутри транзакции сервиса, и подпорка нужна только тестам.
 */
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

    /**
     * УСЛОВИЕ 2 ТЗ-5: договор отделения принадлежит отделению, а не головной
     * организации — отделение у нас отдельный санаторий.
     */
    @Test
    void contractOfBranch_belongsToBranchNotHeadOffice() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, BRANCH_ID, null, false));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().lpu().externalId())
                .isEqualTo(BRANCH_ID);
    }

    @Test
    void contractOfStrangeContragent_isSkipped() {
        ImportResult result = importOne(OneCStream.LPU_CONTRACTS, contract(STRANGER_ID, STRANGER_ID, null, false));

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        assertThat(contractRepository.count()).isZero();
    }

    /**
     * Нулевой GUID головного договора — это «договор сам по себе», а не ссылка
     * на несуществующую запись.
     */
    @Test
    void standaloneContract_hasNoMainContract() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false));

        LpuContract stored = contractRepository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.mainContractExternalId()).isNull();
        assertThat(stored.usePricesMainContract()).isFalse();
    }

    /**
     * ТЗ-5 v2: оба поля источника сохраняются как есть. Признак ценообразующего договора
     * из них больше не выводится — он считается при расчёте цены.
     */
    @Test
    void subordinateContract_keepsBothSourceFields() {
        importOne(OneCStream.LPU_CONTRACTS,
                contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, MAIN_CONTRACT_ID.toString(), true));

        LpuContract stored = contractRepository.findByExternalId(CONTRACT_ID).orElseThrow();
        assertThat(stored.mainContractExternalId()).isEqualTo(MAIN_CONTRACT_ID);
        assertThat(stored.usePricesMainContract()).isTrue();
    }

    /**
     * Головной договор ссылкой не проверяется: он мог не приехать или принадлежать
     * чужому контрагенту, и жёсткий FK превратил бы обычную строку в отказ применения.
     */
    @Test
    void unknownMainContract_isStoredAnyway() {
        importOne(OneCStream.LPU_CONTRACTS,
                contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, MAIN_CONTRACT_ID.toString(), false));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().mainContractExternalId())
                .isEqualTo(MAIN_CONTRACT_ID);
    }

    /** Признак СОБ приходит свойством в {@code extProperties} и сохраняется как данные. */
    @Test
    void sobExportProperty_isStored() {
        importOne(OneCStream.LPU_CONTRACTS, contract(HEAD_OFFICE_ID, HEAD_OFFICE_ID, null, false, true));

        assertThat(contractRepository.findByExternalId(CONTRACT_ID).orElseThrow().uploadedToSob()).isTrue();
    }

    /** Свойства в сообщении нет — договор в СОБ не выгружается, а не «неизвестно». */
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

    /**
     * Тот самый риск, о котором контракт предупреждает отдельно: ребёнок приезжает раньше
     * родителя. Это пропуск с причиной, а не отказ — сообщение остаётся в базе целиком
     * и переобрабатывается переводом строки обратно в NEW, когда договор приедет.
     */
    @Test
    void serviceArrivingBeforeItsContract_isSkippedWithReason() {
        ImportResult result = importOne(OneCStream.CONTRAGENT_SERVICES, service(CONTRACT_ID));

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        assertThat(((ImportResult.Skipped) result).reason()).contains("вне справочника");
        assertThat(serviceRepository.count()).isZero();
    }

    private ImportResult importOne(OneCStream stream, String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private static String contract(UUID ownerId, UUID branchId, String mainContractId, boolean useHeadOfficePrices) {
        return contract(ownerId, branchId, mainContractId, useHeadOfficePrices, null);
    }

    /** @param sobExport {@code null} — свойства в сообщении нет вовсе, а не «false» */
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
