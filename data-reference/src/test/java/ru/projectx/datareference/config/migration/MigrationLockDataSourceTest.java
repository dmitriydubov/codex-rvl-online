package ru.projectx.datareference.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MigrationLockDataSourceTest {

    private static final String TRY_LOCK = "select pg_try_advisory_lock(?)";
    private static final String LOCK = "select pg_advisory_lock(?)";
    private static final String UNLOCK = "select pg_advisory_unlock(?)";

    private final DataSource target = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement tryLock = mock(PreparedStatement.class);
    private final PreparedStatement lock = mock(PreparedStatement.class);
    private final PreparedStatement unlock = mock(PreparedStatement.class);
    private final ResultSet tryLockResult = mock(ResultSet.class);

    @BeforeEach
    void connectionWithStatements() throws SQLException {
        when(target.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(TRY_LOCK)).thenReturn(tryLock);
        when(connection.prepareStatement(LOCK)).thenReturn(lock);
        when(connection.prepareStatement(UNLOCK)).thenReturn(unlock);
        when(tryLock.executeQuery()).thenReturn(tryLockResult);
        when(tryLockResult.next()).thenReturn(true);
    }

    @Test
    void freeLock_isTakenOnTheReturnedConnection_withoutWaiting() throws SQLException {
        when(tryLockResult.getBoolean(1)).thenReturn(true);

        new MigrationLockDataSource(target).getConnection();

        verify(tryLock).setLong(1, MigrationAdvisoryLock.KEY);
        verify(connection, never()).prepareStatement(LOCK);
    }

    @Test
    void lockHeldByAnotherInstance_isAwaitedOnTheSameConnection() throws SQLException {
        when(tryLockResult.getBoolean(1)).thenReturn(false);

        new MigrationLockDataSource(target).getConnection();

        verify(lock).setLong(1, MigrationAdvisoryLock.KEY);
        verify(lock).execute();
    }

    @Test
    void closingTheConnection_releasesTheLockBeforeClosing() throws SQLException {
        when(tryLockResult.getBoolean(1)).thenReturn(true);

        new MigrationLockDataSource(target).getConnection().close();

        InOrder order = inOrder(unlock, connection);
        order.verify(unlock).setLong(1, MigrationAdvisoryLock.KEY);
        order.verify(unlock).execute();
        order.verify(connection).close();
    }

    @Test
    void failedRelease_stillClosesTheConnection_andReportsTheFailure() throws SQLException {
        when(tryLockResult.getBoolean(1)).thenReturn(true);
        SQLException releaseFailed = new SQLException("освобождение не удалось");
        when(unlock.execute()).thenThrow(releaseFailed);
        Connection locked = new MigrationLockDataSource(target).getConnection();

        assertThatThrownBy(locked::close).isSameAs(releaseFailed);
        verify(connection).close();
    }

    @Test
    void failedLock_closesTheConnection_andReportsTheFailure() throws SQLException {
        SQLException lockFailed = new SQLException("блокировка не взята");
        when(tryLock.executeQuery()).thenThrow(lockFailed);
        MigrationLockDataSource dataSource = new MigrationLockDataSource(target);

        assertThatThrownBy(dataSource::getConnection).isSameAs(lockFailed);
        verify(connection).close();
    }

    @Test
    void otherConnectionCalls_goToTheTargetConnection() throws SQLException {
        when(tryLockResult.getBoolean(1)).thenReturn(true);
        when(connection.getAutoCommit()).thenReturn(true);
        Connection locked = new MigrationLockDataSource(target).getConnection();

        assertThat(locked.getAutoCommit()).isTrue();
        locked.setAutoCommit(false);

        verify(connection).setAutoCommit(false);
        verify(connection, never()).close();
    }

    @Test
    void closingTheDataSource_closesAClosableTarget() throws Exception {
        DataSource closable = mock(DataSource.class, withSettings().extraInterfaces(Closeable.class));

        new MigrationLockDataSource(closable).close();

        verify((Closeable) closable).close();
    }

    @Test
    void closingTheDataSource_leavesANonClosableTargetAlone() throws Exception {
        new MigrationLockDataSource(target).close();

        verify(target, never()).getConnection();
        verify(connection, never()).prepareStatement(anyString());
    }
}
