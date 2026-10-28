package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.party.ContractPerson;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyDraft;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.repository.issuance.ContractPersonRepository;
import ru.projectx.contracts.repository.database.LeaderLockRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.PersonSnapshots;
import ru.projectx.contracts.service.lm.LmPolicyRequestFacts;
import ru.projectx.contracts.service.lm.LmPolicyRequestMessages;
import ru.projectx.contracts.service.lm.LmPolicyRequestRecording;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class FencedTransactionRowWaitIT {

    private static final IssueError ARCHIVED = new IssueError("PROGRAM_ARCHIVED", "программа страхования в архиве");
    private static final String HOLD_POLICY_ROW = "select id from lm_policies where id = ? for update";
    private static final String OTHER_INSTANCE = "5c1d9e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f";
    private static final String SHORT_LOCK_WAIT = "set local lock_timeout = '300ms'";
    private static final String ACQUIRE_OWN_ROW = "update leader_lock set client_id = ?, expired_after = ?"
            + " where region = ? and lock_key = ? and (client_id = ? or expired_after < ?)";

    @Autowired
    private LmPolicyRepository policies;

    @Autowired
    private ContractPersonRepository persons;

    @Autowired
    private LmPolicyRequestFacts facts;

    @Autowired
    private LmPolicyRequestMessages messages;

    @Autowired
    private OutboxJournal journal;

    @Autowired
    private RequestStatusPublication statuses;

    @Autowired
    private LeaderLockRepository locks;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final LeaderLock lock = LeaderLock.forNewInstance("fence-" + UUID.randomUUID(), "contracts-background");
    private final UUID request = UUID.randomUUID();
    private final UUID child = UUID.randomUUID();
    private LmPolicyRequestRecording recording;
    private TransactionTemplate transaction;
    private RowRaces races;
    private UUID policyId;

    @BeforeEach
    void newPolicyAndTheLeaderRowOfThisInstance() {
        races = new RowRaces(dataSource, jdbc);
        transaction = new TransactionTemplate(transactions);
        recording = new LmPolicyRequestRecording(policies, facts, messages, journal, statuses,
                new LeadershipFence(locks, lock), clock);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        policyId = policies.saveAndFlush(LmPolicy.open(new LmPolicyDraft(terms, child), Issuances.OPENED)).id();
        persons.saveAndFlush(ContractPerson.snapshot(PersonOwner.lmPolicy(policyId), PersonRole.INSURED,
                PersonSnapshots.of(Tasks.child("0.00")), Issuances.OPENED));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.sql("""
                insert into leader_lock (region, lock_key, client_id, created_date, expired_after)
                values (:group, :key, :instance, :now, :expires)
                """)
                .param("group", lock.group())
                .param("key", lock.key())
                .param("instance", lock.instanceId())
                .param("now", now)
                .param("expires", now.plusMinutes(1))
                .update();
    }

    @AfterEach
    void removePolicyAndLeaderRow() {
        jdbc.sql("delete from outbox_messages where aggregate_id in (:policy, :request)")
                .param("policy", policyId).param("request", request).update();
        jdbc.sql("delete from issuance_requests where individual_request_id = :request").param("request", request).update();
        jdbc.sql("delete from contract_persons where lm_policy_id = :policy").param("policy", policyId).update();
        policies.deleteById(policyId);
        jdbc.sql("delete from leader_lock where region = :group").param("group", lock.group()).update();
    }

    @Test
    void leaderRowRenewal_isNotBlockedWhileTheRefusalWaitsForThePolicyRow() throws SQLException {
        AtomicInteger renewedRows = new AtomicInteger();

        races.whileTheOtherPathWaitsForTheRow(HOLD_POLICY_ROW, policyId,
                () -> transaction.executeWithoutResult(status -> recording.refuse(policyId, ARCHIVED)),
                () -> renewedRows.set(renewOwnLeaderRowWithinAShortLockWait()));

        assertThat(renewedRows).hasValue(1);
        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("ERROR");
        assertThat(row.get("error_code")).isEqualTo("PROGRAM_ARCHIVED");
    }

    @Test
    void leaderRowTakenWhileTheRefusalWaitsForThePolicyRow_failsTheRefusalWithNothingWritten() throws SQLException {
        AtomicReference<LeadershipLostException> refusal = new AtomicReference<>();

        races.whileTheOtherPathWaitsForTheRow(HOLD_POLICY_ROW, policyId, () -> {
            try {
                transaction.executeWithoutResult(status -> recording.refuse(policyId, ARCHIVED));
            }
            catch (LeadershipLostException lost) {
                refusal.set(lost);
            }
        }, this::takeOverTheLeaderRow);

        assertThat(refusal.get()).isNotNull();
        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("error_code")).isNull();
    }

    private int renewOwnLeaderRowWithinAShortLockWait() {
        try (Connection renewal = dataSource.getConnection()) {
            renewal.setAutoCommit(false);
            try (Statement shortWait = renewal.createStatement()) {
                shortWait.execute(SHORT_LOCK_WAIT);
            }
            try (PreparedStatement renew = renewal.prepareStatement(ACQUIRE_OWN_ROW)) {
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                renew.setString(1, lock.instanceId());
                renew.setTimestamp(2, Timestamp.valueOf(now.plusMinutes(2)));
                renew.setString(3, lock.group());
                renew.setString(4, lock.key());
                renew.setString(5, lock.instanceId());
                renew.setTimestamp(6, Timestamp.valueOf(now));
                int rows = renew.executeUpdate();
                renewal.commit();
                return rows;
            }
        }
        catch (SQLException failure) {
            throw new IllegalStateException("продление строки ведущего не прошло, SQLSTATE " + failure.getSQLState(),
                    failure);
        }
    }

    private void takeOverTheLeaderRow() {
        int rows = jdbc.sql("update leader_lock set client_id = :other where region = :group and lock_key = :key")
                .param("other", OTHER_INSTANCE)
                .param("group", lock.group())
                .param("key", lock.key())
                .update();
        assertThat(rows).isEqualTo(1);
    }

    private Map<String, Object> policyRow() {
        return jdbc.sql("select status::text as status, error_code from lm_policies where id = :id")
                .param("id", policyId).query().singleRow();
    }
}
