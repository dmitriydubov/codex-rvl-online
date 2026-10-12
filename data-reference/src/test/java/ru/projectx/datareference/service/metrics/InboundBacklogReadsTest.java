package ru.projectx.datareference.service.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.RejectedMessage;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.QuarantineService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.FixedClockConfig;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.TestJdbc;

@IntegrationTest
class InboundBacklogReadsTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_policies";
    private static final Instant NOW = FixedClockConfig.NOW;

    @Autowired
    private InboundBacklogReads reads;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private QuarantineService quarantineService;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private TestJdbc testJdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void emptyTables_giveEmptySnapshotAtTheCurrentMoment() {
        InboundBacklog backlog = reads.read();

        assertThat(backlog.statuses()).isEmpty();
        assertThat(backlog.stuckTransfers()).isZero();
        assertThat(backlog.overdue()).isEqualTo(new InboundBacklog.Overdue(0, 0, 0));
        assertThat(backlog.takenAt()).isEqualTo(NOW);
    }

    @Test
    void unfinishedMessages_areCountedByStreamAndStatusWithTheOldest() {
        store(OneCStream.POLICIES, InboundStatus.NEW, Duration.ofMinutes(10));
        store(OneCStream.POLICIES, InboundStatus.NEW, Duration.ofMinutes(40));
        store(OneCStream.POLICIES, InboundStatus.FAILED, Duration.ofDays(2));
        store(OneCStream.MKB10, InboundStatus.RETRY, Duration.ofMinutes(5));
        store(OneCStream.MKB10, InboundStatus.PROCESSED, Duration.ofDays(3));
        store(OneCStream.MKB10, InboundStatus.SKIPPED, Duration.ofDays(3));

        InboundBacklog backlog = reads.read();

        assertThat(backlog.statuses()).containsExactlyInAnyOrder(
                new InboundBacklog.StatusCount(OneCStream.POLICIES, InboundStatus.NEW, 2, NOW.minus(Duration.ofMinutes(40))),
                new InboundBacklog.StatusCount(OneCStream.POLICIES, InboundStatus.FAILED, 1, NOW.minus(Duration.ofDays(2))),
                new InboundBacklog.StatusCount(OneCStream.MKB10, InboundStatus.RETRY, 1, NOW.minus(Duration.ofMinutes(5))));
    }

    @Test
    void stuckTransfers_areCountedOncePerTransfer() {
        UUID stuck = UUID.randomUUID();
        buffer(stuck, Duration.ofHours(3));
        buffer(stuck, Duration.ofHours(2));
        buffer(stuck, Duration.ofMinutes(5));
        buffer(UUID.randomUUID(), Duration.ofHours(2));
        buffer(UUID.randomUUID(), Duration.ofMinutes(30));

        assertThat(reads.read().stuckTransfers()).isEqualTo(2);
    }

    @Test
    void overduePayloads_areCountedForPoliciesAndQuarantine() {
        store(OneCStream.POLICIES, InboundStatus.PROCESSED, Duration.ofDays(31));
        store(OneCStream.POLICIES, InboundStatus.PROCESSED, Duration.ofDays(29));
        store(OneCStream.MKB10, InboundStatus.PROCESSED, Duration.ofDays(40));
        UUID erased = store(OneCStream.POLICIES, InboundStatus.PROCESSED, Duration.ofDays(40));
        testJdbc.update("update inbound_message set payload = null where message_id = ?", erased);
        quarantine(Duration.ofDays(31));
        quarantine(Duration.ofDays(35));
        quarantine(Duration.ofDays(1));

        assertThat(reads.read().overdue()).isEqualTo(new InboundBacklog.Overdue(1, 2, 0));
    }

    private UUID store(OneCStream stream, InboundStatus status, Duration age) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope("{\"id\":\"%s\"}".formatted(messageId)));
        testJdbc.update("update inbound_message set status = ?, received_at = ? where message_id = ?",
                status.name(), Timestamp.from(NOW.minus(age)), messageId);
        return messageId;
    }

    private void buffer(UUID transfer, Duration age) {
        UUID part = store(OneCStream.PRICE_LISTS, InboundStatus.BUFFERED, age);
        testJdbc.update("update inbound_message set correlation_id = ? where message_id = ?", transfer, part);
    }

    private void quarantine(Duration age) {
        UUID messageId = UUID.randomUUID();
        quarantineService.quarantine(new RejectedMessage(messageId, OneCStream.POLICIES, QUEUE, new byte[] {1}), "22P05");
        testJdbc.update("update quarantine set received_at = ? where message_id = ?",
                Timestamp.from(NOW.minus(age)), messageId);
    }
}
