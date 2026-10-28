package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
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
import ru.projectx.contracts.entity.party.ContractPerson;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyDraft;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.repository.issuance.ContractPersonRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.PersonSnapshots;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

@IntegrationTest
class LmPolicyRequestRaceIT {

    private static final ExternalIdentifiers IDENTIFIERS = new ExternalIdentifiers(
            UUID.fromString("6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2"), UUID.fromString("8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88"),
            UUID.fromString("1d9a6e77-4c02-4b88-a15e-30f7b2c9de41"));
    private static final IssueError ARCHIVED = new IssueError("PROGRAM_ARCHIVED", "программа страхования в архиве");
    private static final String CANCEL = "update lm_policies set status = 'CANCELLED'::lm_policy_status,"
            + " cancel_reason = 'REQUEST_CANCELLED'::contract_cancel_reason, cancelled_at = now(),"
            + " entity_version = entity_version + 1 where id = ?";
    private static final String HOLD = "select id from lm_policies where id = ? for update";

    @Autowired
    private LmPolicyRepository policies;

    @Autowired
    private ContractPersonRepository persons;

    @Autowired
    private LmPolicyRequestRecording recording;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private final UUID child = UUID.randomUUID();
    private RowRaces races;
    private UUID policyId;

    @BeforeEach
    void newPolicyWithItsInsuredParty() {
        races = new RowRaces(dataSource, jdbc);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        policyId = policies.saveAndFlush(LmPolicy.open(new LmPolicyDraft(terms, child), Issuances.OPENED)).id();
        persons.saveAndFlush(ContractPerson.snapshot(PersonOwner.lmPolicy(policyId), PersonRole.INSURED,
                PersonSnapshots.of(Tasks.child("0.00")), Issuances.OPENED));
    }

    @AfterEach
    void removePolicy() {
        jdbc.sql("delete from outbox_messages where aggregate_id in (:policy, :request)")
                .param("policy", policyId).param("request", request).update();
        jdbc.sql("delete from issuance_requests where individual_request_id = :request").param("request", request).update();
        jdbc.sql("delete from contract_persons where lm_policy_id = :policy").param("policy", policyId).update();
        policies.deleteById(policyId);
    }

    @Test
    void cancellationCommittedWhileTheSubmissionWaitsForTheRow_isKeptAndNoRequestGoesToUsk() throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(CANCEL, policyId, () -> recording.submit(policyId, IDENTIFIERS));

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("entity_version")).isEqualTo(2);
        assertThat(outboxMessages()).isEmpty();
    }

    @Test
    void cancellationCommittedWhileTheRefusalWaitsForTheRow_isKeptWithoutErrorOrAnswer() throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(CANCEL, policyId, () -> recording.refuse(policyId, ARCHIVED));

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("error_code")).isNull();
        assertThat(row.get("entity_version")).isEqualTo(2);
        assertThat(outboxMessages()).isEmpty();
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_failsTheSubmissionWithNothingWritten() throws SQLException {
        races.whileTheRowIsHeld(HOLD, policyId, () ->
                assertThatThrownBy(() -> recording.submit(policyId, IDENTIFIERS))
                        .isInstanceOf(PessimisticLockingFailureException.class));

        Map<String, Object> row = policyRow();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("entity_version")).isEqualTo(1);
        assertThat(outboxMessages()).isEmpty();
    }

    private Map<String, Object> policyRow() {
        return jdbc.sql("select status::text as status, error_code, entity_version from lm_policies where id = :id")
                .param("id", policyId).query().singleRow();
    }

    private List<UUID> outboxMessages() {
        return jdbc.sql("select id from outbox_messages where aggregate_id in (:policy, :request)")
                .param("policy", policyId).param("request", request).query(UUID.class).list();
    }
}
