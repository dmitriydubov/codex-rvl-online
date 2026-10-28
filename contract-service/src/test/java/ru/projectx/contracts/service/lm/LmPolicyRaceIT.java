package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.outbox.OutboxStatus;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.message.usk.LmPolicyEvents;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.service.inbox.InboxFailure;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.outbox.OutboxOutcomes;
import ru.projectx.contracts.service.outbox.PublishOutcome;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class LmPolicyRaceIT {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"),
            "LM-000012204", UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456");
    private static final UskDocument FORM = new UskDocument(PrintedFormType.POLICY,
            "https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf", 96256L);
    private static final PublishOutcome.Refused NO_ROUTE =
            new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)");
    private static final String ISSUE = "update lm_policies set status = 'ISSUED'::lm_policy_status,"
            + " issued_policy_number = '0555123456', entity_version = entity_version + 1 where id = ?";
    private static final String MARK_UNDELIVERED = "update lm_policies set status = 'ERROR'::lm_policy_status,"
            + " error_code = 'LM_POLICY_REQUEST_UNDELIVERED', entity_version = entity_version + 1 where id = ?";
    private static final String HOLD = "select id from lm_policies where id = ? for update";

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
    private RowRaces races;
    private UUID policyId;
    private UUID requestMessageId;

    @BeforeEach
    void policySubmittedToUsk() {
        races = new RowRaces(dataSource, jdbc);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(terms, UUID.randomUUID()), Issuances.OPENED);
        policy.submit(Issuances.OPENED);
        policyId = policies.saveAndFlush(policy).id();
        MessageEnvelope envelope = new MessageEnvelope(UUID.randomUUID(), request,
                LmPolicyEvents.LM_POLICY_REQUEST_TOPIC, LmPolicyEvents.LM_POLICY_REQUESTED, 2);
        requestMessageId = journal.record(new OutboxDraft("LM_POLICY", policyId, envelope,
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
        races.commitOnceTheOtherPathWaitsForTheRow(ISSUE, policyId, () -> outcomes.record(requestMessageId, NO_ROUTE));

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
        races.commitOnceTheOtherPathWaitsForTheRow(MARK_UNDELIVERED, policyId,
                () -> recording.recordIssued(policyId, ISSUED, List.of(FORM), NOW));

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
        races.whileTheRowIsHeld(HOLD, policyId, () ->
                assertThatThrownBy(() -> recording.recordIssued(policyId, ISSUED, List.of(FORM), NOW))
                        .isInstanceOf(PessimisticLockingFailureException.class)
                        .satisfies(failure -> assertThat(InboxFailure.of((RuntimeException) failure))
                                .isEqualTo(new InboxFailure.Transient("временный отказ базы данных, SQLSTATE 55P03",
                                        "55P03"))));

        assertThat(policyRow().get("status")).isEqualTo("SUBMITTED");
        assertThat(printedForms()).isZero();
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_failsTheRequestOutcomeAndLeavesItForTheNextPass()
            throws SQLException {
        races.whileTheRowIsHeld(HOLD, policyId, () ->
                assertThatThrownBy(() -> outcomes.record(requestMessageId, NO_ROUTE))
                        .isInstanceOf(PessimisticLockingFailureException.class));

        assertThat(policyRow().get("status")).isEqualTo("SUBMITTED");
        assertThat(messages.findById(requestMessageId)).hasValueSatisfying(message -> {
            assertThat(message.status()).isEqualTo(OutboxStatus.NEW);
            assertThat(message.attempts()).isZero();
        });
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
