package ru.projectx.datareference.config.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class MigrationAdvisoryLock {

    static final long KEY = MigrationLockDataSource.MIGRATION_LOCK_KEY;

    private static final String TRY_LOCK = "select pg_try_advisory_lock(?)";
    private static final String LOCK = "select pg_advisory_lock(?)";
    private static final String UNLOCK = "select pg_advisory_unlock(?)";

    private MigrationAdvisoryLock() {
    }

    static boolean tryTake(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRY_LOCK)) {
            statement.setLong(1, KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    static void await(Connection connection) throws SQLException {
        execute(connection, LOCK);
    }

    static void release(Connection connection) throws SQLException {
        execute(connection, UNLOCK);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, KEY);
            statement.execute();
        }
    }
}
