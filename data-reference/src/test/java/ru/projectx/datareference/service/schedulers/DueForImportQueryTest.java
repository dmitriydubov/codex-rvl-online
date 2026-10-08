package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

/**
 * Выборка для прохода разбора на настоящем PostgreSQL: сообщение, отложенное до повтора, держит более поздние сообщения
 * своего потока, но не чужих, а удержанные не занимают места в пачке. Часы остановлены, поэтому момент приёма, статус
 * и срок повтора выставляются запросом.
 */
@IntegrationTest
class DueForImportQueryTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final Limit WHOLE_BATCH = Limit.of(100);

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository repository;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    /** Повтор ещё не наступил: более позднее сообщение того же потока ждёт, сообщение другого потока — нет. */
    @Test
    void retryNotYetDue_holdsLaterMessagesOfItsStreamOnly() {
        UUID held = UUID.randomUUID();
        retrying(OneCStream.MKB10, ago(60), NOW.plusSeconds(30));
        pending(held, OneCStream.MKB10, ago(30));
        UUID otherStream = pending(UUID.randomUUID(), OneCStream.INSURANCE_CONTRACTS, ago(20));

        assertThat(due(WHOLE_BATCH)).containsExactly(otherStream);
    }

    /**
     * Срок повтора наступил: голова идёт в пачку первой, а за ней более поздние сообщения её потока. Если голова снова
     * не удастся, их удержит сам проход.
     */
    @Test
    void dueRetry_isTakenAheadOfLaterMessagesOfItsStream() {
        UUID head = retrying(OneCStream.MKB10, ago(60), NOW.minusSeconds(1));
        UUID later = pending(UUID.randomUUID(), OneCStream.MKB10, ago(30));

        assertThat(due(WHOLE_BATCH)).containsExactly(head, later);
    }

    /** Срок повтора, равный текущему моменту, уже наступил: поток не держится. */
    @Test
    void retryDueExactlyNow_isTakenWithLaterMessages() {
        UUID head = retrying(OneCStream.MKB10, ago(60), NOW);
        UUID later = pending(UUID.randomUUID(), OneCStream.MKB10, ago(30));

        assertThat(due(WHOLE_BATCH)).containsExactly(head, later);
    }

    /** Срок повтора наступит через миг: голова ещё ждёт, и поток держится. */
    @Test
    void retryDueJustAfterNow_holdsItsStream() {
        retrying(OneCStream.MKB10, ago(60), NOW.plusMillis(1));
        pending(UUID.randomUUID(), OneCStream.MKB10, ago(30));

        assertThat(due(WHOLE_BATCH)).isEmpty();
    }

    /** Более позднее сообщение, чей срок повтора наступил, ждёт, пока не наступит срок более раннего. */
    @Test
    void laterDueRetry_waitsForEarlierRetryNotYetDue() {
        retrying(OneCStream.MKB10, ago(60), NOW.plusSeconds(30));
        retrying(OneCStream.MKB10, ago(30), NOW.minusSeconds(1));

        assertThat(due(WHOLE_BATCH)).isEmpty();
    }

    /** Держит только более раннее: отложенное сообщение, принятое позже, не мешает тому, что принято раньше. */
    @Test
    void laterRetry_doesNotHoldEarlierMessage() {
        UUID earlier = pending(UUID.randomUUID(), OneCStream.MKB10, ago(60));
        retrying(OneCStream.MKB10, ago(30), NOW.plusSeconds(30));

        assertThat(due(WHOLE_BATCH)).containsExactly(earlier);
    }

    /**
     * Окончательный отказ, разобранные сообщения и части передачи, ждущие остальных частей, поток не держат: держит
     * только отложенное до повтора. Пачка — по возрастанию момента приёма, через все потоки.
     */
    @Test
    void finishedAndBufferedMessages_doNotHoldTheirStream() {
        inStatus(OneCStream.MKB10, ago(90), InboundStatus.FAILED);
        inStatus(OneCStream.MKB10, ago(80), InboundStatus.PROCESSED);
        inStatus(OneCStream.MKB10, ago(75), InboundStatus.SKIPPED);
        inStatus(OneCStream.PRICE_LISTS, ago(70), InboundStatus.BUFFERED);
        UUID price = pending(UUID.randomUUID(), OneCStream.PRICE_LISTS, ago(40));
        UUID code = pending(UUID.randomUUID(), OneCStream.MKB10, ago(30));

        assertThat(due(WHOLE_BATCH)).containsExactly(price, code);
    }

    /** Удержанные сообщения в пачку не попадают: иначе ждущий поток занял бы всю пачку и остановил остальные. */
    @Test
    void heldMessages_doNotTakeTheBatch() {
        retrying(OneCStream.MKB10, ago(100), NOW.plusSeconds(30));
        pending(UUID.randomUUID(), OneCStream.MKB10, ago(90));
        pending(UUID.randomUUID(), OneCStream.MKB10, ago(80));
        UUID otherStream = pending(UUID.randomUUID(), OneCStream.APPEAL_TYPES, ago(10));

        assertThat(due(Limit.of(1))).containsExactly(otherStream);
    }

    private List<UUID> due(Limit limit) {
        return repository.findDueForImport(NOW, limit).stream()
                .map(message -> message.receipt().messageId())
                .toList();
    }

    private UUID pending(UUID messageId, OneCStream stream, Instant receivedAt) {
        inboundMessageService.store(messageId, stream, QUEUE, "{\"data\":{}}");
        testJdbc.update("update inbound_message set received_at = ? where message_id = ?",
                Timestamp.from(receivedAt), messageId);
        return messageId;
    }

    private UUID retrying(OneCStream stream, Instant receivedAt, Instant nextRetryAt) {
        UUID messageId = pending(UUID.randomUUID(), stream, receivedAt);
        testJdbc.update("update inbound_message set status = 'RETRY', attempt = 1, next_retry_at = ? where message_id = ?",
                Timestamp.from(nextRetryAt), messageId);
        return messageId;
    }

    private void inStatus(OneCStream stream, Instant receivedAt, InboundStatus status) {
        UUID messageId = pending(UUID.randomUUID(), stream, receivedAt);
        testJdbc.update("update inbound_message set status = ? where message_id = ?", status.name(), messageId);
    }

    private static Instant ago(long seconds) {
        return NOW.minus(Duration.ofSeconds(seconds));
    }
}
