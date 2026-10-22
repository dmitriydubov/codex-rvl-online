package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.entity.IssuanceTerms;
import ru.projectx.contracts.entity.IssuedPolicy;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.OutboxStatus;
import ru.projectx.contracts.entity.PrintedFormType;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.UskDocument;
import ru.projectx.contracts.message.LmPolicyEvents;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.repository.OutboxMessageRepository;
import ru.projectx.contracts.service.inbox.InboxFailure;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.outbox.OutboxOutcomes;
import ru.projectx.contracts.service.outbox.PublishOutcome;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class LmPolicyRaceIT {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final Duration LOCK_WAIT = Duration.ofSeconds(3);
    private static final Duration OUTCOME_WAIT = Duration.ofSeconds(20);
    private static final IssuedPolicy ISSUED = new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"),
            "LM-000012204", UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456");
    private static final UskDocument FORM = new UskDocument(PrintedFormType.POLICY,
            "https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf", 96256L);
    private static final PublishOutcome.Refused NO_ROUTE =
            new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)");

    @Autowired
    private LmPolicyRepository policies;

    @Autowired
    private OutboxJournal journal;

    @Autowired
    private OutboxMessageRepository messages;

    @Autowired
    private OutboxOutcomes outcomes;

    @Autowired
    private IssuedPolicyRecording recording;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private UUID policyId;
    private UUID requestMessageId;

    @BeforeEach
    void policySubmittedToUsk() {
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(terms, UUID.randomUUID()), Issuances.OPENED);
        policy.submit(Issuances.OPENED);
        policyId = policies.saveAndFlush(policy).id();
        MessageEnvelope envelope = new MessageEnvelope(UUID.randomUUID(), request,
                LmPolicyEvents.LM_POLICY_REQUEST_TOPIC, LmPolicyEvents.LM_POLICY_REQUESTED, 2);
        requestMessageId = journal.record(new OutboxDraft(LmPolicyRequestRecording.AGGREGATE_TYPE, policyId, envelope,
                "{}"), Issuances.OPENED).id();
    }

    @AfterEach
    void removePolicy() {
        jdbc.sql("delete from contract_documents where lm_policy_id = :policy").param("policy", policyId).update();
        jdbc.sql("delete from outbox_messages where aggregate_id = :request").param("request", request).update();
        messages.deleteById(requestMessageId);
        policies.deleteById(policyId);
    }

    @Test
    void issueCommittedWhileTheUndeliveredRequestWaitsForTheRow_isKeptAndTheRequestOutcomeChangesNothing()
            throws SQLException {
        try (Connection usk = dataSource.getConnection()) {
            usk.setAutoCommit(false);
            issueUncommitted(usk);
            Future<?> rejection = inAnotherThread(() -> outcomes.record(requestMessageId, NO_ROUTE));
            commitOnceTheOtherWaitsForTheRow(usk);
            awaitOutcome(rejection);
        }

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("ISSUED");
        assertThat(row.get("error_code")).isNull();
        assertThat(row.get("issued_policy_number")).isEqualTo("0555123456");
        assertThat(messages.findById(requestMessageId)).map(message -> message.status()).contains(OutboxStatus.FAILED);
        assertThat(statusAnswers()).isZero();
    }

    @Test
    void undeliveredRequestCommittedWhileTheIssueWaitsForTheRow_isKeptAndTheIssueChangesNothing()
            throws SQLException {
        try (Connection publisher = dataSource.getConnection()) {
            publisher.setAutoCommit(false);
            markUndeliveredUncommitted(publisher);
            Future<?> issue = inAnotherThread(() -> recording.recordIssued(policyId, ISSUED, List.of(FORM), NOW));
            commitOnceTheOtherWaitsForTheRow(publisher);
            awaitOutcome(issue);
        }

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("ERROR");
        assertThat(row.get("error_code")).isEqualTo("LM_POLICY_REQUEST_UNDELIVERED");
        assertThat(row.get("issued_policy_number")).isNull();
        assertThat(printedForms()).isZero();
        assertThat(statusAnswers()).isZero();
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_isATemporaryFailureOfTheResultWithNothingWritten()
            throws SQLException {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            holdRow(holder);
            try {
                assertThatThrownBy(() -> recording.recordIssued(policyId, ISSUED, List.of(FORM), NOW))
                        .isInstanceOf(PessimisticLockingFailureException.class)
                        .satisfies(failure -> assertThat(InboxFailure.of((RuntimeException) failure))
                                .isEqualTo(new InboxFailure.Transient("временный отказ базы данных, SQLSTATE 55P03",
                                        "55P03")));
            }
            finally {
                holder.rollback();
            }
        }

        assertThat(policyRow().get("status")).isEqualTo("SUBMITTED");
        assertThat(printedForms()).isZero();
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_failsTheRequestOutcomeAndLeavesItForTheNextPass()
            throws SQLException {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            holdRow(holder);
            try {
                assertThatThrownBy(() -> outcomes.record(requestMessageId, NO_ROUTE))
                        .isInstanceOf(PessimisticLockingFailureException.class);
            }
            finally {
                holder.rollback();
            }
        }

        assertThat(policyRow().get("status")).isEqualTo("SUBMITTED");
        assertThat(messages.findById(requestMessageId)).hasValueSatisfying(message -> {
            assertThat(message.status()).isEqualTo(OutboxStatus.NEW);
            assertThat(message.attempts()).isZero();
        });
    }

    private void issueUncommitted(Connection usk) throws SQLException {
        try (PreparedStatement issue = usk.prepareStatement("update lm_policies set status = 'ISSUED'::lm_policy_status,"
                + " issued_policy_number = '0555123456', entity_version = entity_version + 1 where id = ?")) {
            issue.setObject(1, policyId);
            assertThat(issue.executeUpdate()).isEqualTo(1);
        }
    }

    private void markUndeliveredUncommitted(Connection publisher) throws SQLException {
        try (PreparedStatement reject = publisher.prepareStatement("update lm_policies set status = 'ERROR'::lm_policy_status,"
                + " error_code = 'LM_POLICY_REQUEST_UNDELIVERED', entity_version = entity_version + 1 where id = ?")) {
            reject.setObject(1, policyId);
            assertThat(reject.executeUpdate()).isEqualTo(1);
        }
    }

    private void holdRow(Connection holder) throws SQLException {
        try (PreparedStatement lock = holder.prepareStatement("select id from lm_policies where id = ? for update")) {
            lock.setObject(1, policyId);
            lock.executeQuery();
        }
    }

    private void commitOnceTheOtherWaitsForTheRow(Connection connection) throws SQLException {
        Awaitility.await().atMost(LOCK_WAIT).until(() -> jdbc.sql("select count(*) from pg_stat_activity"
                + " where datname = current_database() and wait_event_type = 'Lock'").query(Integer.class).single() > 0);
        connection.commit();
    }

    private static Future<?> inAnotherThread(Runnable path) {
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            return thread.submit(path);
        }
        finally {
            thread.shutdown();
        }
    }

    private static void awaitOutcome(Future<?> path) {
        try {
            path.get(OUTCOME_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ожидание другого пути прервано", interrupted);
        }
        catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("другой путь не завершился", failure);
        }
    }

    private Map<String, Object> policyRow() {
        return jdbc.sql("select status::text as status, error_code, issued_policy_number from lm_policies where id = :id")
                .param("id", policyId).query().singleRow();
    }

    private int printedForms() {
        return jdbc.sql("select count(*) from contract_documents where lm_policy_id = :policy").param("policy", policyId)
                .query(Integer.class).single();
    }

    private int statusAnswers() {
        return jdbc.sql("select count(*) from outbox_messages where aggregate_id = :request").param("request", request)
                .query(Integer.class).single();
    }
}
