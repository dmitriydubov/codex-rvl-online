package ru.projectx.datareference.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.PgConnection;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import ru.projectx.datareference.config.cache.ReferenceCacheNotificationProperties;

class ReconnectingListenerConnectionsTest {

    private static final ReferenceCacheNotificationProperties QUICK =
            new ReferenceCacheNotificationProperties(Duration.ofMillis(1), Duration.ofMillis(2));
    private static final String CANNOT_CONNECT = "08001";

    private final PgConnection connection = mock(PgConnection.class);
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void reachableDatabase_connectsAtFirstAttempt() throws SQLException {
        PgConnection connected = new ReconnectingListenerConnections(failingTimes(0), QUICK).get();

        assertThat(connected).isSameAs(connection);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unreachableDatabase_isRetriedUntilItAnswers() throws SQLException {
        PgConnection connected = new ReconnectingListenerConnections(failingTimes(5), QUICK).get();

        assertThat(connected).isSameAs(connection);
        assertThat(attempts).hasValue(6);
    }

    @Test
    void stopWhileWaitingForDatabase_endsWithSqlException_andKeepsInterruptFlag() {
        Thread.currentThread().interrupt();

        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(() -> new ReconnectingListenerConnections(failingTimes(1), QUICK).get());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(attempts).hasValue(1);
    }

    private PgConnectionSupplier failingTimes(int failures) {
        return () -> {
            if (attempts.incrementAndGet() <= failures) {
                throw new SQLException("база недоступна", CANNOT_CONNECT);
            }
            return connection;
        };
    }
}
