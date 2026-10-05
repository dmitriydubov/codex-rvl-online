package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Проход шедулера: что он забирает и как классифицирует отказ.
 *
 * <p>Проход вызывается напрямую, а не через расписание: тест, ждущий срабатывания
 * таймера, плавает и в итоге его отключают. По расписанию тот же метод вызывает Spring.
 *
 * <p>Утверждения — только о строках, которые тест создал сам, и только по их
 * идентификаторам. Ни {@code findAll()}, ни {@code count()}: шедулер по устройству
 * забирает <b>всё</b> дозревшее сырьё, а не только положенное этим тестом, и проверка
 * состояния всей таблицы падала бы от любой соседней строки — сообщая при этом не о том,
 * что сломан разбор, а о том, что в базе кто-то ещё.
 */
@IntegrationTest
class ReferenceImportSchedulerTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final UUID CHOLERA = UUID.fromString("a4f1c2d3-0000-4000-8000-000000000001");
    private static final UUID TYPHOID = UUID.fromString("a4f1c2d3-0000-4000-8000-000000000002");
    private static final UUID PARATYPHOID = UUID.fromString("a4f1c2d3-0000-4000-8000-000000000003");
    private static final UUID SHIGELLOSIS = UUID.fromString("a4f1c2d3-0000-4000-8000-000000000006");

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ReferenceImportScheduler scheduler;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private Mkb10Repository mkb10Repository;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void pendingMessages_areImportedInOnePass() {
        UUID first = store(mkb10(CHOLERA, "A00"));
        UUID second = store(mkb10(TYPHOID, "A01"));

        scheduler.importPendingMessages();

        assertThat(statusOf(first)).isEqualTo(InboundStatus.PROCESSED);
        assertThat(statusOf(second)).isEqualTo(InboundStatus.PROCESSED);
        assertThat(mkb10Repository.findByExternalId(CHOLERA)).isPresent();
        assertThat(mkb10Repository.findByExternalId(TYPHOID)).isPresent();
    }

    /**
     * Разобранное второй раз не разбирается. Проверяется это на самой выборке шедулера,
     * а не на числе строк справочника: upsert идемпотентен, и повторное применение
     * по счётчику неотличимо от его отсутствия — тест прошёл бы, даже если бы строку
     * забирали каждый проход.
     */
    @Test
    void processedMessages_areNotPickedUpAgain() {
        UUID messageId = store(mkb10(PARATYPHOID, "A02"));
        scheduler.importPendingMessages();

        assertThat(inboundMessageRepository.findDueForImport(clock.instant(), Limit.of(100)))
                .extracting(inbound -> inbound.receipt().messageId())
                .doesNotContain(messageId);
    }

    /**
     * Невалидное сообщение — постоянная ошибка: повторять бессмысленно, строка сразу
     * уходит в терминальный отказ, не тратя попытки лестницы.
     */
    @Test
    void contractViolation_endsWithFailedWithoutSpendingRetries() {
        UUID messageId = store("{\"id\":\"a4f1c2d3-0000-4000-8000-000000000004\",\"name\":\"Без кода\"}");

        scheduler.importPendingMessages();

        InboundMessage failed = inboundMessageRepository.findById(messageId).orElseThrow();
        assertThat(failed.outcome().status()).isEqualTo(InboundStatus.FAILED);
        assertThat(failed.outcome().attempt()).isZero();
        assertThat(failed.outcome().lastError()).contains("code");
    }

    /** Один битый экземпляр не должен останавливать разбор всей пачки. */
    @Test
    void brokenMessage_doesNotStopTheRest() {
        UUID brokenId = store("{\"id\":\"a4f1c2d3-0000-4000-8000-000000000005\",\"name\":\"Без кода\"}");
        UUID goodId = store(mkb10(SHIGELLOSIS, "A03"));

        scheduler.importPendingMessages();

        assertThat(statusOf(brokenId)).isEqualTo(InboundStatus.FAILED);
        assertThat(statusOf(goodId)).isEqualTo(InboundStatus.PROCESSED);
        assertThat(mkb10Repository.findByExternalId(SHIGELLOSIS)).isPresent();
    }

    /** @return message-id сохранённой строки сырья — по нему тест и проверяет исход */
    private UUID store(String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, envelope(data));
        return messageId;
    }

    private InboundStatus statusOf(UUID messageId) {
        return inboundMessageRepository.findById(messageId).orElseThrow().outcome().status();
    }

    private static String mkb10(UUID externalId, String code) {
        return """
                {"id":"%s","code":"%s","name":"Диагноз %s"}"""
                .formatted(externalId, code, code);
    }
}
