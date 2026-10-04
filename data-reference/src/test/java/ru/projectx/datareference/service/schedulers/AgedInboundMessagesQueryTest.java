package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.BufferedTransfer;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.FixedClockConfig;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

/**
 * Запросы к сырым сообщениям старше порога на настоящем PostgreSQL: стирание тел пачкой и выборка зависших
 * передач. Их условия — порядок, предел, группировка — проверяются только базой, unit-тесты видят лишь
 * параметры вызова.
 *
 * <p>Часы остановлены, поэтому «старое» сообщение кладётся через сервис, а время приёма отодвигается
 * запросом.
 */
@IntegrationTest
class AgedInboundMessagesQueryTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final UUID OLD_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000001");
    private static final UUID OLDER_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000002");
    private static final UUID FRESH_TRANSFER = UUID.fromString("c1d2e3f4-0000-4000-8000-000000000003");

    @Autowired
    private AgedInboundMessages agedMessages;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    /** Пачка не больше предела, и первыми стираются самые старые тела. */
    @Test
    void forgetPayloads_erasesOldestRowsUpToLimit() {
        UUID oldest = store(OneCStream.POLICIES, Duration.ofDays(40));
        UUID older = store(OneCStream.POLICIES, Duration.ofDays(35));
        UUID old = store(OneCStream.POLICIES, Duration.ofDays(31));

        int erased = forget(2);

        assertThat(erased).isEqualTo(2);
        assertThat(payloadOf(oldest)).isNull();
        assertThat(payloadOf(older)).isNull();
        assertThat(payloadOf(old)).isNotNull();
    }

    /** Свежие тела, тела другого потока и уже стёртые в пачку не попадают и в счёт не идут. */
    @Test
    void forgetPayloads_skipsFreshOtherStreamAndErasedRows() {
        UUID fresh = store(OneCStream.POLICIES, Duration.ofDays(29));
        UUID reference = store(OneCStream.MKB10, Duration.ofDays(40));
        store(OneCStream.POLICIES, Duration.ofDays(40));
        forget(10);

        assertThat(forget(10)).isZero();
        assertThat(payloadOf(fresh)).isNotNull();
        assertThat(payloadOf(reference)).isNotNull();
    }

    /**
     * По строке на передачу, с числом её частей и временем первой; самые давние первыми и не больше предела.
     * Передача, начатая позже порога, не зависла, даже если у неё много частей.
     */
    @Test
    void transfersBufferedLongerThan_groupsPartsAndOrdersByFirstPart() {
        buffer(OLD_TRANSFER, Duration.ofHours(3));
        buffer(OLD_TRANSFER, Duration.ofMinutes(10));
        buffer(OLDER_TRANSFER, Duration.ofHours(5));
        buffer(FRESH_TRANSFER, Duration.ofMinutes(30));

        assertThat(agedMessages.transfersBufferedLongerThan(Duration.ofHours(1), 10))
                .extracting(BufferedTransfer::getCorrelationId, BufferedTransfer::getFirstPartReceivedAt,
                        BufferedTransfer::getParts)
                .containsExactly(
                        tuple(OLDER_TRANSFER,
                                FixedClockConfig.NOW.minus(Duration.ofHours(5)), 1L),
                        tuple(OLD_TRANSFER,
                                FixedClockConfig.NOW.minus(Duration.ofHours(3)), 2L));
        assertThat(agedMessages.transfersBufferedLongerThan(Duration.ofHours(1), 1))
                .extracting(BufferedTransfer::getCorrelationId).containsExactly(OLDER_TRANSFER);
    }

    /** Разобранная передача больше не в буфере и зависшей не считается. */
    @Test
    void transfersBufferedLongerThan_ignoresRowsOutsideBuffer() {
        UUID processed = store(OneCStream.PRICE_LISTS, Duration.ofHours(5));
        testJdbc.update("update inbound_message set status = 'PROCESSED', correlation_id = ? where message_id = ?",
                OLD_TRANSFER, processed);

        assertThat(agedMessages.transfersBufferedLongerThan(Duration.ofHours(1), 10)).isEmpty();
    }

    private int forget(int limit) {
        return transactions.execute(status -> agedMessages.forgetPayloads(OneCStream.POLICIES, Duration.ofDays(30), limit));
    }

    private void buffer(UUID transfer, Duration age) {
        UUID part = store(OneCStream.PRICE_LISTS, age);
        testJdbc.update("update inbound_message set status = 'BUFFERED', correlation_id = ? where message_id = ?",
                transfer, part);
    }

    private UUID store(OneCStream stream, Duration age) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope("{\"id\":\"%s\"}".formatted(messageId)));
        testJdbc.update("update inbound_message set received_at = ? where message_id = ?",
                Timestamp.from(FixedClockConfig.NOW.minus(age)), messageId);
        return messageId;
    }

    private String payloadOf(UUID messageId) {
        return inboundMessageRepository.findById(messageId).orElseThrow().payload();
    }
}
