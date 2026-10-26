package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
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
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.IssuanceTerms;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.inbox.InboxFailure;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class TaskCancellationRaceIT {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final String ISSUE_POLICY = "update lm_policies set status = 'ISSUED'::lm_policy_status,"
            + " issued_policy_number = '0555123456', entity_version = entity_version + 1 where id = ?";
    private static final String MARK_UNDELIVERED = "update lm_policies set status = 'ERROR'::lm_policy_status,"
            + " error_code = 'LM_POLICY_REQUEST_UNDELIVERED', entity_version = entity_version + 1 where id = ?";
    private static final String ISSUE_CONTRACT = "update contracts set status = 'ISSUED'::contract_status,"
            + " entity_version = entity_version + 1 where id = ?";
    private static final String HOLD_POLICY = "select id from lm_policies where id = ? for update";

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private LmPolicyRepository policies;

    @Autowired
    private TaskCancellationService cancellation;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private RowRaces races;
    private UUID contractId;
    private UUID policyId;

    @BeforeEach
    void contractReadyAndPolicySubmitted() {
        races = new RowRaces(dataSource, jdbc);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        Contract contract = Contract.open(new ContractDraft("DKM-RACE-" + request.toString().substring(0, 8), terms,
                Tasks.SPOUSE, new BigDecimal("34000.00")), Issuances.OPENED);
        contract.offerDraft();
        contractId = contracts.saveAndFlush(contract).id();
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(terms, Tasks.CHILD), Issuances.OPENED);
        policy.submit(Issuances.OPENED);
        policyId = policies.saveAndFlush(policy).id();
    }

    @AfterEach
    void removeRecords() {
        policies.deleteById(policyId);
        contracts.deleteById(contractId);
    }

    @Test
    void undeliveredRequestCommittedWhileTheCancellationWaitsForTheRow_cancelsThePolicyKeepingItsError()
            throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(MARK_UNDELIVERED, policyId, this::cancelRequest);

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("error_code")).isEqualTo("LM_POLICY_REQUEST_UNDELIVERED");
        assertThat(row.get("cancel_reason")).isEqualTo("EXPIRED");
        assertThat(row.get("entity_version")).isEqualTo(4);
    }

    @Test
    void issueCommittedWhileTheCancellationWaitsForThePolicyRow_leavesThePolicyIssuedAndCancelsTheRest()
            throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(ISSUE_POLICY, policyId, this::cancelRequest);

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("ISSUED");
        assertThat(row.get("cancel_reason")).isNull();
        assertThat(row.get("entity_version")).isEqualTo(3);
        assertThat(contractRow().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void issueCommittedWhileTheCancellationWaitsForTheContractRow_leavesTheContractIssuedAndCancelsTheRest()
            throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(ISSUE_CONTRACT, contractId, this::cancelRequest);

        Map<String, Object> row = contractRow();
        assertThat(row.get("status")).isEqualTo("ISSUED");
        assertThat(row.get("cancel_reason")).isNull();
        assertThat(row.get("entity_version")).isEqualTo(2);
        assertThat(policyRow().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_isATemporaryFailureOfTheCancellationWithNothingWritten()
            throws SQLException {
        races.whileTheRowIsHeld(HOLD_POLICY, policyId, () ->
                assertThatThrownBy(this::cancelRequest)
                        .isInstanceOf(PessimisticLockingFailureException.class)
                        .satisfies(failure -> assertThat(InboxFailure.of((RuntimeException) failure))
                                .isEqualTo(new InboxFailure.Transient("временный отказ базы данных, SQLSTATE 55P03", "55P03"))));

        assertThat(policyRow().get("status")).isEqualTo("SUBMITTED");
        assertThat(contractRow().get("status")).isEqualTo("DRAFT_READY");
    }

    private void cancelRequest() {
        transaction.executeWithoutResult(status -> cancellation.apply(
                new ContractTaskCancellation(request, NOW, "EXPIRED", null), NOW));
    }

    private Map<String, Object> policyRow() {
        return jdbc.sql("select status::text as status, error_code, cancel_reason::text as cancel_reason, entity_version"
                + " from lm_policies where id = :id").param("id", policyId).query().singleRow();
    }

    private Map<String, Object> contractRow() {
        return jdbc.sql("select status::text as status, cancel_reason::text as cancel_reason, entity_version"
                + " from contracts where id = :id").param("id", contractId).query().singleRow();
    }
}
