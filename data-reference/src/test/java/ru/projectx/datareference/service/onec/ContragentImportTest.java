package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.catalog.InsurerRepository;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ContragentImportTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_contragents-ul-changes";
    private static final String RVL = "f3876c53-c7d8-4620-b364-9538164ba5d4";
    private static final String OTHER_ACTIVITY = "11111111-2222-3333-4444-555555555555";
    private static final UUID CONTRAGENT_ID = UUID.fromString("3a7e9b10-0000-4000-8000-000000000001");

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
    private InsurerRepository insurerRepository;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void contragentWithRvlActivity_becomesSanatorium() {
        importOne(contragent(true, RVL, "Санаторий «Заря»"));

        Lpu sanatorium = lpuRepository.findByExternalId(CONTRAGENT_ID).orElseThrow();
        assertThat(sanatorium.name()).isEqualTo("Санаторий «Заря»");
        assertThat(sanatorium.inn()).isEqualTo("7701234567");
        assertThat(sanatorium.isArchive()).isFalse();
        assertThat(insurerRepository.count()).isZero();
    }

    @Test
    void contragentWithoutRvlActivity_isSkipped() {
        ImportOutcome result = importOne(contragent(true, OTHER_ACTIVITY, "ООО «Ромашка»"));

        assertThat(result).isInstanceOf(ImportOutcome.Skipped.class);
        assertThat(lpuRepository.count()).isZero();
    }

    @Test
    void knownSanatoriumLosingRvl_isArchivedAndStillUpdated() {
        importOne(contragent(true, RVL, "Санаторий «Заря»"));

        importOne(contragent(true, OTHER_ACTIVITY, "Санаторий «Заря» (переименован)"));

        Lpu sanatorium = lpuRepository.findByExternalId(CONTRAGENT_ID).orElseThrow();
        assertThat(sanatorium.isArchive()).isTrue();
        assertThat(sanatorium.name()).isEqualTo("Санаторий «Заря» (переименован)");
    }

    @Test
    void contragentThatIsNotLpu_becomesInsurer() {
        importOne(contragent(false, OTHER_ACTIVITY, "АО «Работодатель»"));

        assertThat(insurerRepository.findByExternalId(CONTRAGENT_ID)).isPresent();
        assertThat(lpuRepository.count()).isZero();
    }

    @Test
    void insurerIsStored_butMessageIsSkippedWithReason() {
        ImportOutcome result = importOne(contragent(false, OTHER_ACTIVITY, "АО «Работодатель»"));

        assertThat(result)
                .isInstanceOfSatisfying(ImportOutcome.Skipped.class,
                        skipped -> assertThat(skipped.reason()).contains("страхователей"));
        assertThat(insurerRepository.findByExternalId(CONTRAGENT_ID)).isPresent();
    }

    @Test
    void lastMessageWins_thereIsNoVersionGuard() {
        importOne(contragent(true, RVL, "Санаторий «Заря»"));

        ImportOutcome result = importOne(contragent(true, RVL, "Старое название"));

        assertThat(result).isInstanceOf(ImportOutcome.Applied.class);
        assertThat(lpuRepository.findByExternalId(CONTRAGENT_ID).orElseThrow().name())
                .isEqualTo("Старое название");
    }

    private ImportOutcome importOne(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.CONTRAGENTS, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    private static String contragent(boolean thisIsLpu, String activityGuid, String name) {
        return """
                {"guid":"%s","thisIsLPU":%b,"Наименование":"%s","НаименованиеПолное":"%s",\
                "ИНН":"7701234567","КПП":"770101001","Телефон":"+7 495 000-00-00",\
                "typesActivities":[{"guidTypeActivities":"%s"}]}"""
                .formatted(CONTRAGENT_ID, thisIsLpu, name, name, activityGuid);
    }
}
