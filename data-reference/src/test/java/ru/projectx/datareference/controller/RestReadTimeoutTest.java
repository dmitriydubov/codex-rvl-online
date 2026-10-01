package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Лимит времени REST-чтений ({@code projectx.api.read-timeout-seconds}, 3 с по умолчанию): чтение,
 * которое ждёт базу, обрывается по нему и отвечает 503, не дожидаясь {@code lock_timeout} пула (5 с).
 *
 * <p>База «не отвечает» потому, что другой сеанс держит таблицу под ACCESS EXCLUSIVE — её ждёт даже
 * SELECT. Записей не нужно: запрос по несуществующему ключу ждёт блокировку так же.
 */
@IntegrationTest
class RestReadTimeoutTest {

    /** {@code lock_timeout} пула (Ш4): без лимита чтения ответ пришёл бы не раньше. */
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String UNKNOWN_ID = "f662d1c4-5b26-4248-8ba1-47954abd301a";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void emptyDatabase() {
        databaseCleaner.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "programs,            /internal/v1/programs/" + UNKNOWN_ID,
            "policies,            /internal/v1/policies/" + UNKNOWN_ID,
            "policies,            /internal/v1/policies/" + UNKNOWN_ID + "/contract-view",
            "insurance_contracts, /internal/v1/insurance-contracts/" + UNKNOWN_ID,
            "insurance_contracts, /internal/v1/policies/search?contract_external_id=" + UNKNOWN_ID + "&query=ив",
            "insurance_contracts, /internal/v1/lpu?contract_external_id=" + UNKNOWN_ID,
            "lpu,                 /internal/v1/lpu/" + UNKNOWN_ID,
            "lpu,                 /internal/v1/lpu/" + UNKNOWN_ID + "/services",
            "mkb10,               /internal/v1/mkb10?query=I25",
            "mkb10,               /internal/v1/mkb10?codes=I25"})
    void readWaitingForDatabase_isCutByReadLimit_beforeLockTimeout(String lockedTable, String path)
            throws SQLException {

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

    private static void lock(Connection session, String table) throws SQLException {
        try (Statement statement = session.createStatement()) {
            statement.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
        }
    }
}
