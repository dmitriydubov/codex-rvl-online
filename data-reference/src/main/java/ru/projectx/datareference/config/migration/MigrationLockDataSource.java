package ru.projectx.datareference.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class MigrationLockDataSource extends DelegatingDataSource implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MigrationLockDataSource.class);

    public MigrationLockDataSource(DataSource migrations) {
        super(migrations);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = obtainTargetDataSource().getConnection();
        try {
            takeLock(connection);
        } catch (SQLException failed) {
            connection.close();
            throw failed;
        }
        return ReleasingConnection.around(connection);
    }

    @Override
    public void close() throws Exception {
        if (obtainTargetDataSource() instanceof AutoCloseable) {
            ((AutoCloseable) obtainTargetDataSource()).close();
        }
    }

    private static void takeLock(Connection connection) throws SQLException {
        if (MigrationAdvisoryLock.tryTake(connection)) {
            return;
        }
        log.info("миграции схемы накатывает другой экземпляр: старт ждёт их конца");
        MigrationAdvisoryLock.await(connection);
        log.info("другой экземпляр закончил миграции схемы: старт продолжается");
    }
}
