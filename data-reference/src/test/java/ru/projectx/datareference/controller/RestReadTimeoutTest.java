package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.entity.policy.PolicyProgram;
import ru.projectx.datareference.entity.policy.PolicyServiceLine;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class RestReadTimeoutTest {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String UNKNOWN_ID = "f662d1c4-5b26-4248-8ba1-47954abd301a";
    private static final String POLICY_EXTERNAL_ID_TEXT = "f662d1c4-5b26-4248-8ba1-47954abd3002";
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString(POLICY_EXTERNAL_ID_TEXT);
    private static final UUID CONTRACT_EXTERNAL_ID = UUID.fromString("f662d1c4-5b26-4248-8ba1-47954abd3003");
    private static final UUID PROGRAM_EXTERNAL_ID = UUID.fromString("f662d1c4-5b26-4248-8ba1-47954abd3004");
    private static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private PolicyRepository policies;

    @BeforeEach
    void emptyDatabase() {
        databaseCleaner.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "programs,            /internal/v1/programs/" + UNKNOWN_ID,
            "policies,            /internal/v1/policies/" + UNKNOWN_ID,
            "policies,            /internal/v1/policies?external_id=" + UNKNOWN_ID,
            "policies,            /internal/v1/policies/contract-view?external_id=" + UNKNOWN_ID,
            "policies,            /internal/v1/policies?number=П-1&contract_external_id=" + UNKNOWN_ID,
            "insurance_contracts, /internal/v1/insurance-contracts?external_id=" + UNKNOWN_ID,
            "policies,            /internal/v1/policies/" + UNKNOWN_ID + "/contract-view",
            "insurance_contracts, /internal/v1/insurance-contracts/" + UNKNOWN_ID,
            "insurance_contracts, /internal/v1/policies/search?contract_external_id=" + UNKNOWN_ID + "&query=ив",
            "insurance_contracts, /internal/v1/lpu?contract_external_id=" + UNKNOWN_ID,
            "lpu,                 /internal/v1/lpu/" + UNKNOWN_ID,
            "lpu,                 /internal/v1/lpu/" + UNKNOWN_ID + "/services",
            "lpu,                 /internal/v1/lpu/" + UNKNOWN_ID + "/services?need_prices=true",
            "mkb10,               /internal/v1/mkb10?query=I25",
            "mkb10,               /internal/v1/mkb10?codes=I25"})
    void readWaitingForDatabase_isCutByReadLimit_beforeLockTimeout(String lockedTable, String path)
            throws SQLException {

        assertCutByReadLimit(lockedTable, path);
    }

    @ParameterizedTest
    @CsvSource({
            "policy_programs, /internal/v1/policies/{id}",
            "policy_services, /internal/v1/policies/{id}",
            "policy_programs, /internal/v1/policies?external_id=" + POLICY_EXTERNAL_ID_TEXT,
            "policy_services, /internal/v1/policies?external_id=" + POLICY_EXTERNAL_ID_TEXT})
    void tablePartOfExistingPolicyWaitingForDatabase_isCutByReadLimit_beforeLockTimeout(
            String lockedTable, String path) throws SQLException {

        Policy saved = policies.save(policyWithOneProgram());

        assertCutByReadLimit(lockedTable, path.replace("{id}", saved.id().toString()));
    }

    private void assertCutByReadLimit(String lockedTable, String path) throws SQLException {
        try (Connection anotherSession = dataSource.getConnection()) {
            anotherSession.setAutoCommit(false);
            try {
                lock(anotherSession, lockedTable);
                long started = System.nanoTime();

                MvcTestResult result = mvc.get().uri(path).exchange();

                Duration waited = Duration.ofNanos(System.nanoTime() - started);
                assertThat(result).hasStatus(503);
                assertThat(result).bodyJson().extractingPath("$.error.code").isEqualTo("SERVICE_UNAVAILABLE");
                assertThat(waited).isLessThan(LOCK_TIMEOUT);
            }
            finally {
                anotherSession.rollback();
            }
        }
    }

    private static Policy policyWithOneProgram() {
        var details = new Policy.Details(CONTRACT_EXTERNAL_ID, "П-0001", "Тестов", "Тест", null,
                LocalDate.of(1990, 1, 1), null, null, BEGIN, null, true, false);
        var program = new PolicyProgram.Row(PROGRAM_EXTERNAL_ID, BEGIN, null, null, null, null, null, BigDecimal.TEN, true);
        var line = new PolicyServiceLine.Row(PROGRAM_EXTERNAL_ID, 1, BigDecimal.ONE, BEGIN, null, true);
        return Policy.create(POLICY_EXTERNAL_ID, new Policy.Snapshot(details, List.of(program), List.of(line)), NOW);
    }

    private static void lock(Connection session, String table) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
        }
    }
}
