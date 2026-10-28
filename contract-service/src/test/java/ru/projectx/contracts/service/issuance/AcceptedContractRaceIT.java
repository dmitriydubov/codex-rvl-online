package ru.projectx.contracts.service.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.RowRaces;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
class AcceptedContractRaceIT {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final String ACCEPT = "update contracts set status = 'ACCEPTED'::contract_status where id = ?";

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private IssuanceService issuance;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private final UUID spouse = UUID.randomUUID();
    private RowRaces races;
    private UUID contractId;

    @BeforeEach
    void contractReadyForAcceptance() {
        races = new RowRaces(dataSource, jdbc);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        Contract contract = Contract.open(new ContractDraft("DKM-RACE-" + request.toString().substring(0, 8), terms,
                spouse, new BigDecimal("34000.00")), Issuances.OPENED);
        contract.offerDraft();
        contractId = contracts.saveAndFlush(contract).id();
    }

    @AfterEach
    void removeContract() {
        contracts.deleteById(contractId);
    }

    @Test
    void acceptanceCommittedWhileTheTaskWaitsForTheContractRow_isNotOverwrittenByTheTask() throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(ACCEPT, contractId,
                () -> transaction.executeWithoutResult(status -> issuance.apply(raisedPremium(), NOW)));

        assertTheContractStaysAcceptedWithItsPremium();
    }

    @Test
    void acceptanceCommittedAfterTheTaskReadTheContract_isNotOverwrittenByTheTask() {
        transaction.executeWithoutResult(status -> {
            assertThat(contracts.findByRequestIndividualRequestId(request)).hasSize(1);
            acceptElsewhere();
            issuance.apply(raisedPremium(), NOW);
        });

        assertTheContractStaysAcceptedWithItsPremium();
    }

    private ContractTask raisedPremium() {
        return new ContractTask(2, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY, Tasks.FROM, Tasks.TO,
                List.of(Tasks.insured("0.00"), Tasks.relative(1, spouse, "36000.00", "SPOUSE")));
    }

    private void assertTheContractStaysAcceptedWithItsPremium() {
        Map<String, Object> row = jdbc.sql("select status::text as status, premium_amount, entity_version from contracts"
                + " where id = :id").param("id", contractId).query().singleRow();
        assertThat(row.get("status")).isEqualTo("ACCEPTED");
        assertThat((BigDecimal) row.get("premium_amount")).isEqualByComparingTo("34000.00");
        assertThat(row.get("entity_version")).isEqualTo(1);
    }

    private void acceptElsewhere() {
        try (Connection other = dataSource.getConnection();
                PreparedStatement accept = other.prepareStatement(ACCEPT)) {
            other.setAutoCommit(true);
            accept.setObject(1, contractId);
            assertThat(accept.executeUpdate()).isEqualTo(1);
        }
        catch (SQLException failure) {
            throw new IllegalStateException("акцепт в другой транзакции не записан", failure);
        }
    }
}
