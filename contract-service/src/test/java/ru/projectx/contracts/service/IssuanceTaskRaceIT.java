package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
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
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.IssuanceTerms;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.inbox.InboxFailure;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class IssuanceTaskRaceIT {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final String SUBMIT = "update lm_policies set status = 'SUBMITTED'::lm_policy_status,"
            + " entity_version = entity_version + 1 where id = ?";
    private static final String MARK_UNDELIVERED = "update lm_policies set status = 'ERROR'::lm_policy_status,"
            + " error_code = 'LM_POLICY_REQUEST_UNDELIVERED', entity_version = entity_version + 1 where id = ?";
    private static final String HOLD = "select id from lm_policies where id = ? for update";

    @Autowired
    private LmPolicyRepository policies;

    @Autowired
    private IssuanceService issuance;

    @Autowired
    private RequestStatusPublication statuses;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private RowRaces races;
    private UUID policyId;

    @BeforeEach
    void newPolicyOfTheChild() {
        races = new RowRaces(dataSource, jdbc);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        policyId = policies.saveAndFlush(LmPolicy.open(new LmPolicyDraft(terms, Tasks.CHILD), Issuances.OPENED)).id();
    }

    @AfterEach
    void removePolicies() {
        jdbc.sql("delete from contract_persons where lm_policy_id in"
                + " (select id from lm_policies where individual_request_id = :request)").param("request", request).update();
        jdbc.sql("delete from lm_policies where individual_request_id = :request").param("request", request).update();
    }

    @Test
    void submissionCommittedWhileTheTaskWaitsForTheRow_keepsThePolicySubmittedWithItsTermsAndOpensNothing()
            throws SQLException {
        ContractTask movedTrip = new ContractTask(2, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM.plusDays(7), Tasks.TO.plusDays(7), List.of(Tasks.insured("0.00"), Tasks.child("0.00")));

        races.commitOnceTheOtherPathWaitsForTheRow(SUBMIT, policyId, () -> applyAfterWatching(movedTrip));

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("SUBMITTED");
        assertThat(((Date) row.get("date_from")).toLocalDate()).isEqualTo(Tasks.FROM);
        assertThat(row.get("entity_version")).isEqualTo(2);
        assertThat(policiesOfTheRequest()).isEqualTo(1);
    }

    @Test
    void undeliveredRequestCommittedWhileTheTaskExcludingTheChildWaitsForTheRow_leavesThePolicyInError()
            throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(MARK_UNDELIVERED, policyId,
                () -> applyAfterWatching(withoutTheChild()));

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("ERROR");
        assertThat(row.get("error_code")).isEqualTo("LM_POLICY_REQUEST_UNDELIVERED");
        assertThat(row.get("cancel_reason")).isNull();
        assertThat(row.get("entity_version")).isEqualTo(2);
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_isATemporaryFailureOfTheTaskWithNothingWritten() throws SQLException {
        races.whileTheRowIsHeld(HOLD, policyId, () ->
                assertThatThrownBy(() -> applyAfterWatching(withoutTheChild()))
                        .isInstanceOf(PessimisticLockingFailureException.class)
                        .satisfies(failure -> assertThat(InboxFailure.of((RuntimeException) failure))
                                .isEqualTo(new InboxFailure.Transient("временный отказ базы данных, SQLSTATE 55P03", "55P03"))));

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("entity_version")).isEqualTo(1);
    }

    private void applyAfterWatching(ContractTask task) {
        transaction.executeWithoutResult(status -> {
            statuses.watch(request);
            issuance.apply(task, NOW);
        });
    }

    private ContractTask withoutTheChild() {
        return new ContractTask(2, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY, Tasks.FROM, Tasks.TO,
                List.of(Tasks.insured("0.00")));
    }

    private Map<String, Object> policyRow() {
        return jdbc.sql("select status::text as status, error_code, cancel_reason::text as cancel_reason, date_from,"
                + " entity_version from lm_policies where id = :id").param("id", policyId).query().singleRow();
    }

    private int policiesOfTheRequest() {
        return jdbc.sql("select count(*) from lm_policies where individual_request_id = :request")
                .param("request", request).query(Integer.class).single();
    }
}
