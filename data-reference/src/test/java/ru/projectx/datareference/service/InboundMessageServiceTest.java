package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Хранение сырья и переходы его статуса на настоящем PostgreSQL: колонка {@code jsonb}
 * и умолчания схемы на встроенной базе ведут себя иначе, чем в контуре.
 */
@IntegrationTest
class InboundMessageServiceTest {

    private static final ParseFailure.Temporary TIMEOUT = new ParseFailure.Temporary(
            "временный отказ базы данных: QueryTimeoutException, SQLSTATE 57014", "QueryTimeoutException", "57014");

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = """
            {"data":{"id":"7d2b1f0e-0000-4000-8000-000000000001","code":"A00"},\
            "metadata":{"message-id":"7d2b1f0e-0000-4000-8000-0000000000ff"}}""";

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository repository;

    @BeforeEach
    void clearRawMessages() {
        databaseCleaner.clear();
    }

    @Test
    void storedMessage_startsAsNew() {
        UUID messageId = store();

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.NEW);
        assertThat(stored.outcome().attempt()).isZero();
        assertThat(stored.receipt().receivedAt()).isEqualTo(NOW);
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isNull();
    }

    /**
     * Сырьё хранится содержимым, а не пересказом: маппер читает исходный JSON.
     *
     * <p>Сравнение по дереву, а не по строке, и это не послабление: {@code jsonb}
     * складывает документ в своём виде — пробелы теряются, порядок ключей не
     * сохраняется. Строковое сравнение проверяло бы форматирование PostgreSQL,
     * а не то, что мы сохранили.
     */
    @Test
    void storedMessage_keepsPayloadContentAndSource() {
        UUID messageId = store();

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(JSON.readTree(stored.payload())).isEqualTo(JSON.readTree(PAYLOAD));
        assertThat(stored.receipt().stream()).isEqualTo(OneCStream.MKB10);
        assertThat(stored.receipt().sourceQueue()).isEqualTo(QUEUE);
    }

    @Test
    void processedMessage_recordsProcessedAt() {
        UUID messageId = store();

        inboundMessageService.markProcessed(messageId);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(stored.outcome().processedAt()).isEqualTo(NOW);
    }

    /** Пропуск без причины неотличим от «данных не было» — причина обязана сохраниться. */
    @Test
    void skippedMessage_keepsReason() {
        UUID messageId = store();

        inboundMessageService.markSkipped(messageId, "контрагент без вида деятельности РВЛ");

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
        assertThat(stored.outcome().lastError()).isEqualTo("контрагент без вида деятельности РВЛ");
        assertThat(stored.outcome().processedAt()).isEqualTo(NOW);
    }

    @Test
    void temporaryFailure_schedulesRetryByFirstStepOfLadder() {
        UUID messageId = store();

        inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.RETRY);
        assertThat(stored.outcome().attempt()).isEqualTo(1);
        assertThat(stored.outcome().nextRetryAt()).isEqualTo(NOW.plus(Duration.ofSeconds(30)));
        assertThat(stored.outcome().lastError()).isEqualTo(TIMEOUT.reason());
    }

    /** Умолчание лестницы — 30 с / 5 мин / 30 мин, и каждая следующая попытка идёт по ней. */
    @Test
    void repeatedTemporaryFailures_walkDownTheLadder() {
        UUID messageId = store();

        inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);
        inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);

        assertThat(repository.findById(messageId).orElseThrow().outcome().nextRetryAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().attempt()).isEqualTo(3);
        assertThat(stored.outcome().nextRetryAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    /**
     * Исчерпанная лестница — терминальный отказ, а не бесконечный повтор последним шагом:
     * сообщение, которое не удаётся применить, чинится руками, а не временем.
     */
    @Test
    void exhaustedLadder_endsWithFailed() {
        UUID messageId = store();

        for (int failure = 0; failure < 4; failure++) {
            inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);
        }

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.FAILED);
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isEqualTo("повторы исчерпаны на попытке 4: " + TIMEOUT.reason());
    }

    /** Постоянная ошибка не тратит попытки: повтор даст тот же результат. */
    @Test
    void permanentFailure_endsWithoutRetrying() {
        UUID messageId = store();

        inboundMessageService.recordPermanentFailure(messageId,
                new ParseFailure.Permanent("в конверте нет объекта data", "OneCFieldException", "нет"));

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.FAILED);
        assertThat(stored.outcome().attempt()).isZero();
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isEqualTo("в конверте нет объекта data");
    }

    /**
     * Повтор после временной ошибки может закончиться успехом — тогда от неудачи
     * не должно остаться ни назначенной попытки, ни текста ошибки.
     */
    @Test
    void successAfterRetry_clearsFailureTrace() {
        UUID messageId = store();
        inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);

        inboundMessageService.markProcessed(messageId);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isNull();
    }

    /**
     * Отказ, пришедший после фиксации исхода, читает статус из базы в своей транзакции: применённое сообщение остаётся
     * применённым, попытки не тратятся.
     */
    @Test
    void temporaryFailureAfterProcessed_keepsStoredOutcome() {
        UUID messageId = store();
        inboundMessageService.markProcessed(messageId);

        inboundMessageService.recordTemporaryFailure(messageId, TIMEOUT);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(stored.outcome().attempt()).isZero();
        assertThat(stored.outcome().nextRetryAt()).isNull();
    }

    private UUID store() {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, PAYLOAD);
        return messageId;
    }
}
