package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class DatabaseConnectionSettingsTest {

    private static final String DISABLED = "0";
    private static final String ROW_VALUE = "Тестовое значение строки";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SpringLiquibase liquibase;

    @Test
    void applicationConnection_carriesSessionAndSocketTimeouts() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(setting(connection, "lock_timeout")).isEqualTo("5s");
            assertThat(setting(connection, "statement_timeout")).isEqualTo("30s");
            assertThat(setting(connection, "idle_in_transaction_session_timeout")).isEqualTo("1min");
            assertThat(connection.getNetworkTimeout()).isEqualTo((int) Duration.ofSeconds(60).toMillis());
        }
    }

    @Test
    void applicationPool_waitsForConnectionTwoSeconds() throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);

        assertThat(pool.getPoolName()).isEqualTo("application");
        assertThat(pool.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(2).toMillis());
        assertThat(pool.getValidationTimeout()).isEqualTo(Duration.ofSeconds(1).toMillis());
    }

    @Test
    void migrationConnection_hasNoTimeouts() throws SQLException {
        assertThat(liquibase.getDataSource()).isNotSameAs(dataSource);
        try (Connection connection = liquibase.getDataSource().getConnection()) {
            assertThat(setting(connection, "lock_timeout")).isEqualTo(DISABLED);
            assertThat(setting(connection, "statement_timeout")).isEqualTo(DISABLED);
            assertThat(setting(connection, "idle_in_transaction_session_timeout")).isEqualTo(DISABLED);
            assertThat(connection.getNetworkTimeout()).isZero();
        }
    }

    @Test
    void databaseError_doesNotCarryRowValues() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("CREATE TEMPORARY TABLE party (full_name text PRIMARY KEY)");
                statement.execute("INSERT INTO party VALUES ('" + ROW_VALUE + "')");

                SQLException failure = catchThrowableOfType(SQLException.class,
                        () -> statement.execute("INSERT INTO party VALUES ('" + ROW_VALUE + "')"));

                assertThat(failure.getSQLState()).isEqualTo("23505");
                assertThat(failure.getMessage()).contains("party_pkey").doesNotContain(ROW_VALUE);
            }
            finally {
                connection.rollback();
            }
        }
    }

    private static String setting(Connection connection, String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SHOW " + name)) {
            result.next();
            return result.getString(1);
        }
    }
}
