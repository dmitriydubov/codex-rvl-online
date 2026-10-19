package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundOutcome;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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

        inboundMessageService.recordTemporaryFailure(messageId, unchanged(messageId), TIMEOUT);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.RETRY);
        assertThat(stored.outcome().attempt()).isEqualTo(1);
        assertThat(stored.outcome().nextRetryAt()).isEqualTo(NOW.plus(Duration.ofSeconds(30)));
        assertThat(stored.outcome().lastError()).isEqualTo(TIMEOUT.reason());
    }

    @Test
    void repeatedTemporaryFailures_walkDownTheLadder() {
        UUID messageId = store();

        inboundMessageService.recordTemporaryFailure(messageId, unchanged(messageId), TIMEOUT);
        inboundMessageService.recordTemporaryFailure(messageId, unchanged(messageId), TIMEOUT);

        assertThat(repository.findById(messageId).orElseThrow().outcome().nextRetryAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        inboundMessageService.recordTemporaryFailure(messageId, unchanged(messageId), TIMEOUT);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().attempt()).isEqualTo(3);
        assertThat(stored.outcome().nextRetryAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void exhaustedLadder_endsWithFailed() {
        UUID messageId = store();

        for (int failure = 0; failure < 4; failure++) {
            inboundMessageService.recordTemporaryFailure(messageId, unchanged(messageId), TIMEOUT);
        }

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.FAILED);
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isEqualTo("повторы исчерпаны на попытке 4: " + TIMEOUT.reason());
    }

    @Test
    void permanentFailure_endsWithoutRetrying() {
        UUID messageId = store();

        inboundMessageService.recordPermanentFailure(messageId, unchanged(messageId),
                new ParseFailure.Permanent("в конверте нет объекта data", "OneCFieldException", "нет"));

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.FAILED);
        assertThat(stored.outcome().attempt()).isZero();
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isEqualTo("в конверте нет объекта data");
    }

    @Test
    void successAfterRetry_clearsFailureTrace() {
        UUID messageId = store();
        inboundMessageService.recordTemporaryFailure(messageId, unchanged(messageId), TIMEOUT);

        inboundMessageService.markProcessed(messageId);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(stored.outcome().nextRetryAt()).isNull();
        assertThat(stored.outcome().lastError()).isNull();
    }

    @Test
    void temporaryFailureAfterProcessed_keepsStoredOutcome() {
        UUID messageId = store();
        InboundOutcome beforeAttempt = unchanged(messageId);
        inboundMessageService.markProcessed(messageId);

        inboundMessageService.recordTemporaryFailure(messageId, beforeAttempt, TIMEOUT);

        InboundMessage stored = repository.findById(messageId).orElseThrow();
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.PROCESSED);
        assertThat(stored.outcome().attempt()).isZero();
        assertThat(stored.outcome().nextRetryAt()).isNull();
    }

    private InboundOutcome unchanged(UUID messageId) {
        return repository.findById(messageId).orElseThrow().outcome();
    }

    private UUID store() {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, PAYLOAD);
        return messageId;
    }
}
