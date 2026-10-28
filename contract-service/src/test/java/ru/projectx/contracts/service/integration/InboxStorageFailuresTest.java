package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.integration.leader.Context;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.RejectedMessage;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.receipt.QuarantineService;
import ru.projectx.contracts.service.integration.consumer.InboxConsumerPause;
import ru.projectx.contracts.service.integration.consumer.StorageOutage;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.support.TaskBodies;
import ru.projectx.contracts.support.TestLeadership;

class InboxStorageFailuresTest {

    private static final InboxReceipt RECEIPT = new InboxReceipt(UUID.fromString(TaskBodies.MESSAGE_ID),
            UUID.fromString(TaskBodies.REQUEST_ID), "rvl.contract-task.upserted", "ContractTaskUpserted", 1);
    private static final Duration BACKOFF = Duration.ofSeconds(5);
    private static final String REASON = "в теле символ, который база не хранит: нулевой или непарный суррогат";

    private final QuarantineService quarantine = mock();
    private final InboxConsumerPause consumers = mock();
    private final InboxMetrics metrics = mock();
    private final Pauses pauses = mock();
    private final Channel channel = mock();
    private final Context election = mock();
    private final BackgroundLeadership leadership = TestLeadership.standby();
    private final InboxStorageFailures failures = new InboxStorageFailures(quarantine, consumers,
            new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                    new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                    new InboxProperties.Pause(Duration.ofSeconds(10), Duration.ofSeconds(30)), BACKOFF),
            metrics, leadership, pauses);
    private final String body = TaskBodies.upserted().toJson().replace("Тестова", "Тест\\u0000ова");
    private final TaskDelivery delivery = new TaskDelivery(message(body), channel);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(InboxStorageFailures.class);

    @BeforeEach
    void leaderCollectingJournal() {
        when(election.isLeader()).thenReturn(true);
        leadership.onGranted(election);
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void poisonBody_isQuarantinedWithItsSqlStateCountedAndAcknowledged() throws IOException {
        given(quarantine.quarantine(any(), any())).willReturn(true);

        failures.handle(delivery, RECEIPT, new DataIntegrityViolationException("Тестова", sql("22P05")));

        assertThat(quarantined("SQLSTATE 22P05")).usingRecursiveComparison()
                .isEqualTo(new RejectedMessage(RECEIPT.messageId(), "task-changes", body.getBytes()));
        verify(metrics).countQuarantined();
        verify(channel).basicAck(7L, false);
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("сообщение " + TaskBodies.MESSAGE_ID + " из очереди task-changes помещено в карантин: "
                            + "SQLSTATE 22P05 (" + body.getBytes().length + " байт)");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void unstorableText_isQuarantinedWithTheServiceReason() throws IOException {
        given(quarantine.quarantine(any(), any())).willReturn(true);

        failures.quarantine(delivery, RECEIPT, REASON);

        assertThat(quarantined(REASON)).usingRecursiveComparison()
                .isEqualTo(new RejectedMessage(RECEIPT.messageId(), "task-changes", body.getBytes()));
        verify(metrics).countQuarantined();
        verify(channel).basicAck(7L, false);
        assertThat(journal.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("помещено в карантин: " + REASON).doesNotContain("Тест"));
    }

    @Test
    void messageAlreadyInQuarantine_isAcknowledgedWithoutASecondCount() throws IOException {
        given(quarantine.quarantine(any(), any())).willReturn(false);

        failures.quarantine(delivery, RECEIPT, REASON);

        verify(channel).basicAck(7L, false);
        verifyNoInteractions(metrics);
        assertThat(journal.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void quarantineKeyTakenByAConcurrentDelivery_isAcknowledged() throws IOException {
        given(quarantine.quarantine(any(), any()))
                .willThrow(new DataIntegrityViolationException("дубль", sql("23505")));

        failures.quarantine(delivery, RECEIPT, REASON);

        verify(channel).basicAck(7L, false);
        verifyNoInteractions(metrics, consumers);
    }

    @Test
    void quarantineWriteRejectedAsPoisonToo_pausesTheConsumersWithThatSqlState() throws IOException {
        given(quarantine.quarantine(any(), any()))
                .willThrow(new DataIntegrityViolationException("карантин", sql("22021")));

        failures.quarantine(delivery, RECEIPT, REASON);

        verify(consumers).pause(new StorageOutage("22021", DataIntegrityViolationException.class.getName()));
        verify(channel, never()).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, true);
        verifyNoInteractions(metrics);
    }

    @Test
    void quarantineWriteRefusedByTheDatabase_pausesTheConsumersWithoutAnswer() throws IOException {
        given(quarantine.quarantine(any(), any()))
                .willThrow(new DataAccessResourceFailureException("обрыв", sql("08006")));

        failures.quarantine(delivery, RECEIPT, REASON);

        verify(consumers).pause(new StorageOutage("08006", DataAccessResourceFailureException.class.getName()));
        verify(channel, never()).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, true);
        verifyNoInteractions(metrics);
    }

    @Test
    void duplicateRace_returnsTheMessageAfterTheBackoff() throws IOException {
        given(pauses.hold(BACKOFF)).willReturn(true);

        failures.handle(delivery, RECEIPT, new DataIntegrityViolationException("дубль", sql("23505")));

        verify(pauses).hold(BACKOFF);
        verify(channel).basicNack(7L, false, true);
        verifyNoInteractions(consumers, quarantine);
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(TaskBodies.MESSAGE_ID, "PT5S",
                    DataIntegrityViolationException.class.getName()).doesNotContain("дубль");
        });
    }

    @Test
    void interruptedBackoff_leavesTheMessageUnanswered() throws IOException {
        given(pauses.hold(BACKOFF)).willReturn(false);

        failures.handle(delivery, RECEIPT, new DataIntegrityViolationException("дубль", sql("23505")));

        verify(channel, never()).basicNack(7L, false, true);
        verify(channel, never()).basicAck(7L, false);
    }

    @Test
    void temporaryOutage_pausesTheConsumersAndLeavesTheMessageUnanswered() throws IOException {
        failures.handle(delivery, RECEIPT, new IllegalStateException("Тестова", sql("55P03")));

        verify(consumers).pause(new StorageOutage("55P03", IllegalStateException.class.getName()));
        verify(channel, never()).basicAck(7L, false);
        verify(channel, never()).basicNack(7L, false, true);
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains(TaskBodies.MESSAGE_ID, "java.lang.IllegalStateException, SQLSTATE 55P03")
                    .doesNotContain("Тестова");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void outageWithoutSqlState_isReportedWithoutACode() throws IOException {
        failures.handle(delivery, RECEIPT, new IllegalStateException("база"));

        verify(consumers).pause(new StorageOutage("нет", IllegalStateException.class.getName()));
    }

    @Test
    void lostLeaderLockWhileQuarantining_standsDownWithoutAcknowledging() throws IOException {
        given(quarantine.quarantine(any(), any()))
                .willThrow(new LeadershipLostException(TestLeadership.LOCK.instanceId()));

        failures.quarantine(delivery, RECEIPT, REASON);

        verify(channel, never()).basicAck(7L, false);
        verify(consumers).stopConsumersWithoutProbe();
        verify(consumers, never()).pause(any());
        verify(election).yield();
        verifyNoInteractions(metrics);
    }

    @Test
    void lostLeaderLock_leavesTheMessageUnansweredStopsConsumersWithoutProbeAndYieldsTheRole() throws IOException {
        failures.handle(delivery, RECEIPT, new LeadershipLostException(TestLeadership.LOCK.instanceId()));

        verify(channel, never()).basicNack(7L, false, true);
        verify(channel, never()).basicAck(7L, false);
        verify(consumers).stopConsumersWithoutProbe();
        verify(consumers, never()).pause(any());
        verify(election).yield();
        verifyNoInteractions(quarantine, metrics, pauses);
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains(TaskBodies.MESSAGE_ID, "task-changes", "больше не ведущий");
        });
    }

    private RejectedMessage quarantined(String reason) {
        ArgumentCaptor<RejectedMessage> rejected = ArgumentCaptor.captor();
        verify(quarantine).quarantine(rejected.capture(), eq(reason));
        return rejected.getValue();
    }

    private static SQLException sql(String state) {
        return new SQLException("Тестова", state);
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("task-changes");
        properties.setDeliveryTag(7L);
        return new Message(body.getBytes(), properties);
    }
}
