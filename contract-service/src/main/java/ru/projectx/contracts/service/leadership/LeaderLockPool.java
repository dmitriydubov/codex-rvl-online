package ru.projectx.contracts.service.leadership;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import ru.projectx.contracts.config.DatabaseTimeoutsProperties;

public final class LeaderLockPool implements LeaderLockConnections, AutoCloseable {

    static final String POOL_NAME = "leader-lock";
    static final String SESSION_OPTIONS_PROPERTY = "options";
    private static final String SESSION_OPTIONS_WITHOUT_LOCK_TIMEOUT =
            "-c statement_timeout=%s -c idle_in_transaction_session_timeout=%s";

    private final HikariDataSource pool;

    private LeaderLockPool(HikariDataSource pool) {
        this.pool = pool;
    }

    public static LeaderLockPool besideApplicationPool(DataSource application, DatabaseTimeoutsProperties timeouts,
            int connections, MeterRegistry registry) {
        HikariDataSource pool = new HikariDataSource();
        configure(pool, applicationPool(application), timeouts, connections);
        pool.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
        return new LeaderLockPool(pool);
    }

    private static HikariDataSource applicationPool(DataSource application) {
        return Optional.ofNullable(DataSourceUnwrapper.unwrap(application, HikariConfigMXBean.class, HikariDataSource.class))
                .orElseThrow(() -> new IllegalStateException(
                        "пул соединений приложения не HikariCP: " + application.getClass().getName()));
    }

    private static void configure(HikariConfig target, HikariDataSource application, DatabaseTimeoutsProperties timeouts,
            int connections) {
        target.setPoolName(POOL_NAME);
        target.setJdbcUrl(application.getJdbcUrl());
        target.setUsername(application.getUsername());
        target.setPassword(application.getPassword());
        Optional.ofNullable(application.getDriverClassName()).ifPresent(target::setDriverClassName);
        target.setConnectionTimeout(application.getConnectionTimeout());
        target.setValidationTimeout(application.getValidationTimeout());
        target.setMaximumPoolSize(connections);
        target.setMinimumIdle(connections);
        target.setDataSourceProperties(sessionWithoutLockTimeout(application.getDataSourceProperties(), timeouts));
    }

    private static Properties sessionWithoutLockTimeout(Properties application, DatabaseTimeoutsProperties timeouts) {
        Properties session = new Properties();
        session.putAll(application);
        session.setProperty(SESSION_OPTIONS_PROPERTY, SESSION_OPTIONS_WITHOUT_LOCK_TIMEOUT.formatted(
                timeouts.statementTimeout(), timeouts.idleInTransactionSessionTimeout()));
        return session;
    }

    @Override
    public DataSource dataSource() {
        return pool;
    }

    @Override
    public void close() {
        pool.close();
    }
}
