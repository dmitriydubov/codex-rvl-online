package ru.projectx.datareference.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.lockservice.LockService;
import liquibase.lockservice.StandardLockService;

final class LiquibaseLockServices {

    private LiquibaseLockServices() {
    }

    static LockService of(Connection connection) throws SQLException {
        try {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            StandardLockService lockService = new StandardLockService();
            lockService.setDatabase(database);
            return lockService;
        } catch (DatabaseException failed) {
            throw new SQLException("не удалось открыть замок миграций Liquibase", failed);
        }
    }
}
