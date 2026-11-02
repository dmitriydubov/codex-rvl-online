package ru.projectx.datareference.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import liquibase.exception.LiquibaseException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StaleMigrationLock {

    private static final Logger log = LoggerFactory.getLogger(StaleMigrationLock.class);
    private static final DateTimeFormatter GRANT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final LockServiceSource lockServices;

    StaleMigrationLock(LockServiceSource lockServices) {
        this.lockServices = lockServices;
    }

    void releaseIfLeft(Connection connection) throws SQLException {
        try {
            LockService lockService = lockServices.of(connection);
            DatabaseChangeLogLock[] left = lockService.listLocks();
            if (left.length > 0) {
                lockService.forceReleaseLock();
                report(left);
            }
        } catch (LiquibaseException failed) {
            throw new SQLException("не удалось снять оставшийся замок миграций", failed);
        }
    }

    private static void report(DatabaseChangeLogLock[] released) {
        for (DatabaseChangeLogLock lock : released) {
            log.warn("снят замок миграций, оставшийся от экземпляра {}, взятый {}: экземпляр погиб посреди миграций",
                    lock.getLockedBy(), grantTime(lock));
        }
    }

    private static String grantTime(DatabaseChangeLogLock lock) {
        return GRANT_TIME.format(LocalDateTime.ofInstant(lock.getLockGranted().toInstant(), ZoneId.systemDefault()));
    }

    @FunctionalInterface
    interface LockServiceSource {

        LockService of(Connection connection) throws SQLException;
    }
}
