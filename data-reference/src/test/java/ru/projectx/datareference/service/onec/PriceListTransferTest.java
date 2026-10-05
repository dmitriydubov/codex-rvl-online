package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;
import static ru.projectx.datareference.support.OneCMessages.sanatorium;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Защищает главное правило прайс-листов (ТЗ-9): прайс по услуге применяется только целиком.
 *
 * <p>Прайс по одной услуге приходит несколькими пакетами строк и одним финальным сообщением со счётчиками.
 * Если применить часть пакетов, в справочнике окажется неполный прайс, и никто об этом не узнает.
 */
@IntegrationTest
class PriceListTransferTest {

    private static final String BATCH_QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes";
    private static final String FINAL_QUEUE = BATCH_QUEUE + "-final";

    private static final UUID SANATORIUM_ID = UUID.fromString("8e4b0f70-0000-4000-8000-000000000001");
    private static final UUID CONTRACT_ID = UUID.fromString("8e4b0f70-0000-4000-8000-000000000002");
    private static final UUID SERVICE_ID = UUID.fromString("8e4b0f70-0000-4000-8000-000000000003");
    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");

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

    @Autowired
    private PriceListItemRepository priceRepository;

    @BeforeEach
    void seedServiceChain() {
        databaseCleaner.clear();

        importOne(OneCStream.CONTRAGENTS, BATCH_QUEUE, sanatorium(SANATORIUM_ID, "Санаторий «Заря»"));
        importOne(OneCStream.LPU_CONTRACTS, BATCH_QUEUE, """
                {"id":"%s","ownerId":"%s","contragentBranchId":"%s",\
                "mainContractId":"00000000-0000-0000-0000-000000000000","number":"Д-1",\
                "beginDate":"2024-01-01"}"""
                .formatted(CONTRACT_ID, SANATORIUM_ID, SANATORIUM_ID));
        importOne(OneCStream.CONTRAGENT_SERVICES, BATCH_QUEUE, """
                {"id":"%s","ownerId":"%s","code":"SKL-01","name":"Путёвка, стандарт"}"""
                .formatted(SERVICE_ID, CONTRACT_ID));
    }

    /** Пока не пришло финальное, применять нечего — части ждут в буфере. */
    @Test
    void batchWithoutFinalMessage_waitsInBuffer() {
        UUID batchId = importBatch(rows(1, "4999.95"));

        assertThat(inboundMessageRepository.findById(batchId).orElseThrow().outcome().status())
                .isEqualTo(InboundStatus.BUFFERED);
        assertThat(priceRepository.count()).isZero();
    }

    @Test
    void completeTransfer_replacesPriceList() {
        importBatch(rows(1, "4999.95"));
        importBatch(rows(2, "5999.00"));

        importFinal(2, 2);

        List<PriceListItem> prices = priceRepository.findAll();
        assertThat(prices).hasSize(2);
        assertThat(prices).extracting(PriceListItem::price)
                .containsExactlyInAnyOrder(new BigDecimal("4999.95"), new BigDecimal("5999.00"));
    }

    /** Собранная передача закрывает все свои сообщения разом, а не только последнее. */
    @Test
    void completeTransfer_marksEveryMessageProcessed() {
        importBatch(rows(1, "4999.95"));
        importFinal(1, 1);

        assertThat(inboundMessageRepository.findByCorrelationId(CID))
                .extracting(inbound -> inbound.outcome().status())
                .containsOnly(InboundStatus.PROCESSED);
    }

    /**
     * Порядок двух очередей не гарантирован: финальное может прийти раньше последнего
     * пакета. Условие сборки проверяется на каждом сообщении, поэтому передача
     * соберётся и в этом случае.
     */
    @Test
    void finalMessageArrivingFirst_stillAssemblesTransfer() {
        importFinal(2, 2);
        importBatch(rows(1, "4999.95"));

        importBatch(rows(2, "5999.00"));

        assertThat(priceRepository.count()).isEqualTo(2);
    }

    /**
     * Счётчики не сошлись — вся передача в ошибку, прежний прайс не трогается.
     * Применить неполный прайс значит записать заведомо неверные цены и не узнать
     * об этом никогда.
     */
    @Test
    void mismatchedCounters_failWholeTransferAndKeepPreviousPrices() {
        importBatch(rows(1, "4999.95"));
        importBatch(rows(2, "5999.00"));

        importFinal(2, 5);

        assertThat(priceRepository.count()).isZero();
        assertThat(inboundMessageRepository.findByCorrelationId(CID))
                .extracting(inbound -> inbound.outcome().status())
                .containsOnly(InboundStatus.FAILED);
        assertThat(inboundMessageRepository.findByCorrelationId(CID).getFirst().outcome().lastError())
                .contains("счётчики передачи");
    }

    /** {@code countMessages = 0} — легальный случай: прайс по услуге очищается. */
    @Test
    void emptyTransfer_clearsPriceList() {
        importBatch(rows(1, "4999.95"));
        importFinal(1, 1);
        assertThat(priceRepository.count()).isEqualTo(1);

        importFinalOfNewTransfer(0, 0);

        assertThat(priceRepository.count()).isZero();
    }

    /** Прайс по услуге, которой у нас нет, применять некуда. */
    @Test
    void priceForUnknownService_isSkipped() {
        serviceRepository.deleteAll();

        importBatch(rows(1, "4999.95"));
        importFinal(1, 1);

        assertThat(priceRepository.count()).isZero();
        assertThat(inboundMessageRepository.findByCorrelationId(CID))
                .extracting(inbound -> inbound.outcome().status())
                .containsOnly(InboundStatus.SKIPPED);
    }

    private UUID importBatch(String rows) {
        return importOne(OneCStream.PRICE_LISTS, BATCH_QUEUE, """
                {"cid":"%s","keyValue":"%s","recordSet":[%s]}""".formatted(CID, SERVICE_ID, rows));
    }

    private void importFinal(int countMessages, int countRows) {
        importOne(OneCStream.PRICE_LISTS, FINAL_QUEUE, """
                {"cid":"%s","keyValue":"%s","countMessages":%d,"countRows":%d}"""
                .formatted(CID, SERVICE_ID, countMessages, countRows));
    }

    /** Очистка прайса приходит новой передачей — со своим {@code cid} и без пакетов. */
    private void importFinalOfNewTransfer(int countMessages, int countRows) {
        UUID emptyCid = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000d");
        importOne(OneCStream.PRICE_LISTS, FINAL_QUEUE, """
                {"cid":"%s","keyValue":"%s","countMessages":%d,"countRows":%d}"""
                .formatted(emptyCid, SERVICE_ID, countMessages, countRows));
    }

    private UUID importOne(OneCStream stream, String queue, String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, queue, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        importService.importMessage(message);
        return messageId;
    }

    private static String rows(int rowNumber, String price) {
        return """
                {"rowNumber":%d,"nameService":"Путёвка, стандарт","serviceCode":"SKL-01",\
                "price":%s,"period":"2024-01-01"}""".formatted(rowNumber, price);
    }
}
