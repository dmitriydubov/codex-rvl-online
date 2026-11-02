package ru.projectx.datareference.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;

final class LiquibaseLockServices {

    private LiquibaseLockServices() {
    }

    static LockService of(Connection connection) throws SQLException {
        try {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            return LockServiceFactory.getInstance().getLockService(database);
        } catch (DatabaseException failed) {
            throw new SQLException("не удалось открыть замок миграций Liquibase", failed);
        }
    }
}
