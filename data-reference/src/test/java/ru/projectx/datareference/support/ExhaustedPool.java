package ru.projectx.datareference.support;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import ru.projectx.datareference.config.RestReadDataSource;

public final class ExhaustedPool implements AutoCloseable {

    private final List<Connection> held;

    private ExhaustedPool(List<Connection> held) {
        this.held = held;
    }

    public static ExhaustedPool of(DataSource dataSource) throws SQLException {
        return take(dataSource.unwrap(RestReadDataSource.class).applicationPool());
    }

    public static ExhaustedPool ofRestReads(DataSource dataSource) throws SQLException {
        return take(dataSource.unwrap(RestReadDataSource.class).restReadPool());
    }

    private static ExhaustedPool take(HikariDataSource pool) throws SQLException {
        List<Connection> held = new ArrayList<>();
        try {
            for (int taken = 0; taken < pool.getMaximumPoolSize(); taken++) {
                held.add(pool.getConnection());
            }
            return new ExhaustedPool(held);
        }
        catch (SQLException failure) {
            releaseAfterFailure(held, failure);
            throw failure;
        }
    }

    private static void releaseAfterFailure(List<Connection> held, SQLException failure) {
        try {
            new ExhaustedPool(held).close();
        }
        catch (SQLException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    @Override
    public void close() throws SQLException {
        for (Connection connection : held) {
            connection.close();
        }
    }
}
