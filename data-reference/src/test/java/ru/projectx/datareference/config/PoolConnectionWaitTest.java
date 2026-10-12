package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class PoolConnectionWaitTest {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);
    private static final String CONNECTION_EXCEPTION_CLASS = "08";

    @Autowired
    private DataSource dataSource;

    @Test
    void serverNotListening_poolRefusesAfterConnectionTimeout() throws SQLException, IOException {
        try (HikariDataSource unreachable = new HikariDataSource(configTo(closedPortUrl()))) {
            long started = System.nanoTime();

            Throwable failure = catchThrowable(unreachable::getConnection);

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertThat(failure).isInstanceOfSatisfying(SQLTransientConnectionException.class,
                    refused -> assertThat(refused.getSQLState()).startsWith(CONNECTION_EXCEPTION_CLASS));
            assertThat(waited).isBetween(CONNECTION_TIMEOUT, LOCK_TIMEOUT);
        }
    }

    private HikariConfig configTo(String jdbcUrl) throws SQLException {
        HikariConfig config = new HikariConfig();
        dataSource.unwrap(HikariDataSource.class).copyStateTo(config);
        config.setJdbcUrl(jdbcUrl);
        config.setPoolName("unreachable-database");
        config.setInitializationFailTimeout(-1);
        return config;
    }

    private static String closedPortUrl() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "jdbc:postgresql://127.0.0.1:" + probe.getLocalPort() + "/data_reference";
        }
    }
}
