package ru.projectx.contracts.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class RowRaces {

    private static final Duration LOCK_WAIT = Duration.ofSeconds(3);
    private static final Duration OUTCOME_WAIT = Duration.ofSeconds(20);

    private final DataSource dataSource;
    private final JdbcClient jdbc;

    public RowRaces(DataSource dataSource, JdbcClient jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    public void commitOnceTheOtherPathWaitsForTheRow(String uncommittedChange, UUID rowId, Runnable otherPath)
            throws SQLException {
        try (Connection first = dataSource.getConnection()) {
            first.setAutoCommit(false);
            execute(first, uncommittedChange, rowId);
            Future<?> other = inAnotherThread(otherPath);
            Awaitility.await().atMost(LOCK_WAIT).until(() -> other.isDone() || somebodyWaitsForALock());
            first.commit();
            awaitOutcome(other);
        }
    }

    public void whileTheOtherPathWaitsForTheRow(String holdingStatement, UUID rowId, Runnable otherPath,
            Runnable meanwhile) throws SQLException {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            execute(holder, holdingStatement, rowId);
            Future<?> other = inAnotherThread(otherPath);
            try {
                Awaitility.await().atMost(LOCK_WAIT).until(() -> other.isDone() || somebodyWaitsForALock());
                meanwhile.run();
            }
            finally {
                holder.rollback();
            }
            awaitOutcome(other);
        }
    }

    public void whileTheRowIsHeld(String holdingStatement, UUID rowId, Runnable path) throws SQLException {
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            execute(holder, holdingStatement, rowId);
            try {
                path.run();
            }
            finally {
                holder.rollback();
            }
        }
    }

    private static void execute(Connection connection, String sql, UUID rowId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, rowId);
            if (!statement.execute()) {
                assertThat(statement.getUpdateCount()).isEqualTo(1);
            }
        }
    }

    private boolean somebodyWaitsForALock() {
        return jdbc.sql("select count(*) from pg_stat_activity where datname = current_database()"
                + " and wait_event_type = 'Lock'").query(Integer.class).single() > 0;
    }

    private static Future<?> inAnotherThread(Runnable path) {
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            return thread.submit(path);
        }
        finally {
            thread.shutdown();
        }
    }

    private static void awaitOutcome(Future<?> path) {
        try {
            path.get(OUTCOME_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ожидание другого пути прервано", interrupted);
        }
        catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("другой путь не завершился", failure);
        }
    }
}
