package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;
import static ru.projectx.datareference.support.OneCMessages.sanatorium;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * ТЗ-8: адрес и регион приходят отдельным потоком — тем же, которым едут телефоны
 * и почта всех контрагентов 1С:УСК.
 */
@IntegrationTest
class LpuContactImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_contact-information-changes";
    private static final UUID SANATORIUM_ID = UUID.fromString("5b1e7c40-0000-4000-8000-000000000001");

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

    @BeforeEach
    void seedSanatorium() {
        databaseCleaner.clear();
        importOne(OneCStream.CONTRAGENTS, sanatorium(SANATORIUM_ID, "Санаторий «Заря»"));
    }

    @Test
    void addressRow_fillsAddressAndRegion() {
        importOne(OneCStream.LPU_CONTACTS, contacts("""
                {"type":"Адрес","rowNumber":1,"kind":{"representation":"Фактический адрес контрагента"},\
                "representation":"354000, Сочи, ул. Курортная, 1","field2":"Краснодарский край"}"""));

        Lpu stored = lpuRepository.findByExternalId(SANATORIUM_ID).orElseThrow();
        assertThat(stored.address()).isEqualTo("354000, Сочи, ул. Курортная, 1");
        assertThat(stored.region()).isEqualTo("Краснодарский край");
    }

    /**
     * Приоритет видов адреса задан конфигурацией: фактический важнее адреса регистрации,
     * и порядок строк в сообщении на это не влияет.
     */
    @Test
    void severalAddresses_takeTheOneWithHigherPriority() {
        importOne(OneCStream.LPU_CONTACTS, contacts("""
                {"type":"Адрес","rowNumber":1,\
                "kind":{"representation":"Адрес местонахождения (регистрации) контрагента юр.лица"},\
                "representation":"Юридический адрес","field2":"Москва"},\
                {"type":"Адрес","rowNumber":2,"kind":{"representation":"Фактический адрес контрагента"},\
                "representation":"Фактический адрес","field2":"Краснодарский край"}"""));

        assertThat(lpuRepository.findByExternalId(SANATORIUM_ID).orElseThrow().address())
                .isEqualTo("Фактический адрес");
    }

    /** Телефоны и почта едут тем же потоком — этому маппингу они не нужны. */
    @Test
    void nonAddressRows_areIgnored() {
        importOne(OneCStream.LPU_CONTACTS, contacts("""
                {"type":"Телефон","rowNumber":1,"representation":"+7 495 000-00-00"}"""));

        assertThat(lpuRepository.findByExternalId(SANATORIUM_ID).orElseThrow().address()).isNull();
    }

    /** Источник помечает недействительные адреса сам; такой адрес — не адрес. */
    @Test
    void invalidAddress_isIgnored() {
        importOne(OneCStream.LPU_CONTACTS, contacts("""
                {"type":"Адрес","rowNumber":1,"notValid":true,\
                "kind":{"representation":"Фактический адрес контрагента"},\
                "representation":"Старый адрес","field2":"Москва"}"""));

        assertThat(lpuRepository.findByExternalId(SANATORIUM_ID).orElseThrow().address()).isNull();
    }

    /**
     * Режим замещения: подходящих строк в сообщении нет — адрес очищается. Оставить
     * прежний значило бы показывать в каталоге адрес, которого в 1С уже нет, и никогда
     * об этом не узнать.
     */
    @Test
    void messageWithoutAddresses_clearsPreviousOne() {
        importOne(OneCStream.LPU_CONTACTS, contacts("""
                {"type":"Адрес","rowNumber":1,"kind":{"representation":"Фактический адрес контрагента"},\
                "representation":"354000, Сочи, ул. Курортная, 1","field2":"Краснодарский край"}"""));

        importOne(OneCStream.LPU_CONTACTS, contacts("""
                {"type":"Телефон","rowNumber":1,"representation":"+7 495 000-00-00"}"""));

        Lpu stored = lpuRepository.findByExternalId(SANATORIUM_ID).orElseThrow();
        assertThat(stored.address()).isNull();
        assertThat(stored.region()).isNull();
    }

    @Test
    void contactsOfUnknownContragent_areSkipped() {
        UUID stranger = UUID.fromString("5b1e7c40-0000-4000-8000-0000000000ff");

        ImportResult result = importOne(OneCStream.LPU_CONTACTS, """
                {"filter":[{"fieldName":"object","value":{"value":"%s"}}],"recordSet":[]}""".formatted(stranger));

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
    }

    private ImportResult importOne(OneCStream stream, String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    /** Владелец контактов лежит в фильтре сообщения, строки набора — в {@code recordSet}. */
    private static String contacts(String rows) {
        return """
                {"filter":[{"fieldName":"object","value":{"value":"%s"}}],"recordSet":[%s]}"""
                .formatted(SANATORIUM_ID, rows);
    }
}
