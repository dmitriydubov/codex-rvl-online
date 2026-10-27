package ru.projectx.datareference.acceptance;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

final class SwitchableDataSource extends DelegatingDataSource {

    private final DatabaseSwitch availability;

    SwitchableDataSource(DataSource pool, DatabaseSwitch availability) {
        super(pool);
        this.availability = availability;
    }

    void failConnectionsWith(String sqlState) {
        availability.failConnectionsWith(sqlState);
    }

    void restore() {
        availability.restore();
    }

    @Override
    public Connection getConnection() throws SQLException {
        failIfUnavailable();
        return super.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        failIfUnavailable();
        return super.getConnection(username, password);
    }

    public void close() {
        if (obtainTargetDataSource() instanceof HikariDataSource pool) {
            pool.close();
        }
    }

    private void failIfUnavailable() throws SQLException {
        Optional<String> sqlState = availability.failure();
        if (sqlState.isPresent()) {
            throw new SQLTransientConnectionException("стенд приёмки: база недоступна", sqlState.get());
        }
    }
}
