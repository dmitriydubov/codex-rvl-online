package ru.projectx.datareference.config.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.jdbc.PgConnection;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import org.springframework.util.backoff.BackOffExecution;

class ReconnectingListenerConnectionsTest {

    private static final int RETRIES = 3;
    private static final ReferenceCacheNotificationProperties QUICK = new ReferenceCacheNotificationProperties(
            Duration.ofMillis(1), Duration.ofMillis(2), RETRIES, Duration.ofSeconds(60));
    private static final String CANNOT_CONNECT = "08001";

    private final PgConnection connection = mock(PgConnection.class);
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void reachableDatabase_connectsAtFirstAttempt_andIsReportedConnected() throws SQLException {
        ReconnectingListenerConnections connections = new ReconnectingListenerConnections(failingTimes(0), QUICK);
        assertThat(connections.connected()).as("до первого подключения").isFalse();

        PgConnection connected = connections.get();

        assertThat(connected).isSameAs(connection);
        assertThat(attempts).hasValue(1);
        assertThat(connections.connected()).isTrue();
    }

    @Test
    void unreachableDatabase_isRetriedUntilItAnswers() throws SQLException {
        PgConnection connected = new ReconnectingListenerConnections(failingTimes(RETRIES), QUICK).get();

        assertThat(connected).isSameAs(connection);
        assertThat(attempts).hasValue(RETRIES + 1);
    }

    @Test
    void longOutage_endsSeriesWithSqlException_andNextCallStartsNewSeries() throws SQLException {
        ReconnectingListenerConnections connections =
                new ReconnectingListenerConnections(failingTimes(RETRIES + 1), QUICK);

        assertThatExceptionOfType(SQLException.class).isThrownBy(connections::get);
        assertThat(attempts).hasValue(RETRIES + 1);
        assertThat(connections.connected()).isFalse();

        assertThat(connections.get()).isSameAs(connection);
        assertThat(attempts).hasValue(RETRIES + 2);
    }

    @Test
    void lostConnection_isReportedDisconnectedWhileReconnecting() throws SQLException {
        ReconnectingListenerConnections connections = new ReconnectingListenerConnections(() -> {
            if (attempts.incrementAndGet() > 1) {
                throw new SQLException("база недоступна", CANNOT_CONNECT);
            }
            return connection;
        }, QUICK);
        connections.get();

        assertThatExceptionOfType(SQLException.class).isThrownBy(connections::get);
        assertThat(connections.connected()).isFalse();
    }

    @Test
    void stopWhileWaitingForDatabase_endsWithSqlException_andKeepsInterruptFlag() {
        Thread.currentThread().interrupt();

        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(() -> new ReconnectingListenerConnections(failingTimes(1), QUICK).get());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void pausesDoubleFromDelayUpToMaxDelay_forConfiguredRetries() {
        ReferenceCacheNotificationProperties defaults = new ReferenceCacheNotificationProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(10), 6, Duration.ofSeconds(60));
        BackOffExecution pauses = ReconnectingListenerConnections.retryPolicy(defaults).getBackOff().start();

        assertThat(LongStream.range(0, 7).map(attempt -> pauses.nextBackOff()).toArray())
                .containsExactly(1000, 2000, 4000, 8000, 10000, 10000, BackOffExecution.STOP);
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
