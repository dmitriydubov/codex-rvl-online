package ru.projectx.datareference.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import liquibase.exception.LockException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class StaleMigrationLockTest {

    private static final String LOCKED_BY = "data-reference-7f9c4 (10.0.3.17)";
    private static final LocalDateTime GRANTED = LocalDateTime.of(2026, 11, 1, 23, 40);

    private final Connection connection = mock(Connection.class);
    private final LockService lockService = mock(LockService.class);
    private final StaleMigrationLock staleLock = new StaleMigrationLock(held -> lockService);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void collect() {
        logged.start();
        staleLockLog().addAppender(logged);
    }

    @AfterEach
    void release() {
        staleLockLog().detachAppender(logged);
    }

    @Test
    void lockLeftByDeadInstance_isForceReleased() throws Exception {
        when(lockService.listLocks()).thenReturn(new DatabaseChangeLogLock[] {deadInstanceLock()});

        staleLock.releaseIfLeft(connection);

        verify(lockService).forceReleaseLock();
    }

    @Test
    void releasedLock_isReportedWithItsHolderAndGrantTime() throws Exception {
        when(lockService.listLocks()).thenReturn(new DatabaseChangeLogLock[] {deadInstanceLock()});

        staleLock.releaseIfLeft(connection);

        assertThat(logged.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(LOCKED_BY).contains("2026-11-01T23:40:00");
        });
    }

    @Test
    void freeLock_isLeftAlone() throws Exception {
        when(lockService.listLocks()).thenReturn(new DatabaseChangeLogLock[0]);

        staleLock.releaseIfLeft(connection);

        verify(lockService, never()).forceReleaseLock();
    }

    @Test
    void failedRelease_isReportedAsDatabaseFailure() throws Exception {
        LockException failed = new LockException("замок не снят");
        when(lockService.listLocks()).thenThrow(failed);

        assertThatThrownBy(() -> staleLock.releaseIfLeft(connection))
                .isInstanceOf(SQLException.class).hasCause(failed);
    }

    @Test
    void freeLock_isNotReported() throws Exception {
        when(lockService.listLocks()).thenReturn(new DatabaseChangeLogLock[0]);

        staleLock.releaseIfLeft(connection);

        assertThat(logged.list).isEmpty();
    }

    private static Logger staleLockLog() {
        return (Logger) LoggerFactory.getLogger(StaleMigrationLock.class);
    }

    private static DatabaseChangeLogLock deadInstanceLock() {
        return new DatabaseChangeLogLock(1, Timestamp.valueOf(GRANTED), LOCKED_BY);
    }
}
