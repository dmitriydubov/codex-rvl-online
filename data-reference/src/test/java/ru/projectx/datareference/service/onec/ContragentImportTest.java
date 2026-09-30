package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * ТЗ-3: один поток, два справочника и фильтр, который отсекает почти всё.
 *
 * <p>Поток несёт всех контрагентов-юрлиц 1С:УСК, а нужны нам единицы — санатории
 * с видом деятельности РВЛ. Поэтому здесь проверяется не столько маппинг полей,
 * сколько то, какие сообщения вообще доходят до таблиц.
 */
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

    /** Контрагент без РВЛ — это просто не наше юрлицо, а не ошибка. */
    @Test
    void contragentWithoutRvlActivity_isSkipped() {
        ImportResult result = importOne(contragent(true, OTHER_ACTIVITY, "ООО «Ромашка»"));

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        assertThat(lpuRepository.count()).isZero();
    }

    /**
     * ТЗ-3, п. 3.3: у известного нам санатория пропал вид деятельности РВЛ. Запись
     * не удаляется — физического удаления нет нигде, — а помечается архивной.
     * Реквизиты при этом обновляются: сообщение принесло актуальные.
     */
    @Test
    void knownSanatoriumLosingRvl_isArchivedAndStillUpdated() {
        importOne(contragent(true, RVL, "Санаторий «Заря»"));

        importOne(contragent(true, OTHER_ACTIVITY, "Санаторий «Заря» (переименован)"));

        Lpu sanatorium = lpuRepository.findByExternalId(CONTRAGENT_ID).orElseThrow();
        assertThat(sanatorium.isArchive()).isTrue();
        assertThat(sanatorium.name()).isEqualTo("Санаторий «Заря» (переименован)");
    }

    /** Ветка Б: не ЛПУ — значит страхователь, и фильтра у него нет. */
    @Test
    void contragentThatIsNotLpu_becomesInsurer() {
        importOne(contragent(false, OTHER_ACTIVITY, "АО «Работодатель»"));

        assertThat(insurerRepository.findByExternalId(CONTRAGENT_ID)).isPresent();
        assertThat(lpuRepository.count()).isZero();
    }

    /**
     * Страхователь записан — но исход всё равно пропуск, и причина называет, куда он лёг.
     * Иначе «в lpu пусто, потому что таких контрагентов не присылали» и «пусто, потому
     * что все они оказались страхователями» выглядят в сырье одинаково.
     */
    @Test
    void insurerIsStored_butMessageIsSkippedWithReason() {
        ImportResult result = importOne(contragent(false, OTHER_ACTIVITY, "АО «Работодатель»"));

        assertThat(result)
                .isInstanceOfSatisfying(ImportResult.Skipped.class,
                        skipped -> assertThat(skipped.reason()).contains("страхователей"));
        assertThat(insurerRepository.findByExternalId(CONTRAGENT_ID)).isPresent();
    }

    /**
     * Версии у ТЗ-3 нет, и защиты от переприменения тоже: действует последнее полученное
     * сообщение, даже если по содержанию оно старее.
     */
    @Test
    void lastMessageWins_thereIsNoVersionGuard() {
        importOne(contragent(true, RVL, "Санаторий «Заря»"));

        ImportResult result = importOne(contragent(true, RVL, "Старое название"));

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(lpuRepository.findByExternalId(CONTRAGENT_ID).orElseThrow().name())
                .isEqualTo("Старое название");
    }

    private ImportResult importOne(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.CONTRAGENTS, QUEUE, envelope(data));
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();
        return importService.importMessage(message);
    }

    /** Имена полей кириллические — это ключи JSON источника, а не наш словарь. */
    private static String contragent(boolean thisIsLpu, String activityGuid, String name) {
        return """
                {"guid":"%s","thisIsLPU":%b,"Наименование":"%s","НаименованиеПолное":"%s",\
                "ИНН":"7701234567","КПП":"770101001","Телефон":"+7 495 000-00-00",\
                "typesActivities":[{"guidTypeActivities":"%s"}]}"""
                .formatted(CONTRAGENT_ID, thisIsLpu, name, name, activityGuid);
    }
}
