package ru.projectx.contracts.service.leadership;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceUnwrapper;

public final class LeaderLockPool implements LeaderLockConnections, AutoCloseable {

    static final String POOL_NAME = "leader-lock";
    static final String SESSION_OPTIONS_PROPERTY = "options";
    static final String LOCK_TIMEOUT_DISABLED = "-c lock_timeout=0";
    private static final Pattern LOCK_TIMEOUT_OPTION = Pattern.compile("-c\\s+lock_timeout=\\S+");

    private final HikariDataSource pool;

    private LeaderLockPool(HikariDataSource pool) {
        this.pool = pool;
    }

    public static LeaderLockPool besideApplicationPool(DataSource application, int connections, MeterRegistry registry) {
        HikariDataSource source = applicationPool(application);
        HikariDataSource pool = new HikariDataSource();
        copyConnection(pool, source);
        copyTimeouts(pool, source);
        copyBehaviour(pool, source);
        pool.setPoolName(POOL_NAME);
        pool.setMaximumPoolSize(connections);
        pool.setMinimumIdle(connections);
        pool.setDataSourceProperties(sessionWithoutLockTimeout(source.getDataSourceProperties()));
        pool.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
        return new LeaderLockPool(pool);
    }

    private static HikariDataSource applicationPool(DataSource application) {
        return Optional.ofNullable(DataSourceUnwrapper.unwrap(application, HikariConfigMXBean.class, HikariDataSource.class))
                .orElseThrow(() -> new IllegalStateException(
                        "пул соединений приложения не HikariCP: " + application.getClass().getName()));
    }

    private static void copyConnection(HikariConfig target, HikariConfig source) {
        target.setJdbcUrl(source.getJdbcUrl());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        Optional.ofNullable(source.getDriverClassName()).ifPresent(target::setDriverClassName);
        target.setDataSourceClassName(source.getDataSourceClassName());
        target.setSchema(source.getSchema());
        target.setCatalog(source.getCatalog());
        target.setConnectionInitSql(source.getConnectionInitSql());
        target.setConnectionTestQuery(source.getConnectionTestQuery());
        Optional.ofNullable(source.getExceptionOverrideClassName()).ifPresent(target::setExceptionOverrideClassName);
    }

    private static void copyTimeouts(HikariConfig target, HikariConfig source) {
        target.setConnectionTimeout(source.getConnectionTimeout());
        target.setValidationTimeout(source.getValidationTimeout());
        target.setIdleTimeout(source.getIdleTimeout());
        target.setMaxLifetime(source.getMaxLifetime());
        target.setKeepaliveTime(source.getKeepaliveTime());
        target.setLeakDetectionThreshold(source.getLeakDetectionThreshold());
        target.setInitializationFailTimeout(source.getInitializationFailTimeout());
    }

    private static void copyBehaviour(HikariConfig target, HikariConfig source) {
        target.setAutoCommit(source.isAutoCommit());
        target.setReadOnly(source.isReadOnly());
        target.setIsolateInternalQueries(source.isIsolateInternalQueries());
        target.setTransactionIsolation(source.getTransactionIsolation());
        target.setRegisterMbeans(source.isRegisterMbeans());
        target.setAllowPoolSuspension(source.isAllowPoolSuspension());
    }

    private static Properties sessionWithoutLockTimeout(Properties application) {
        Properties session = new Properties();
        session.putAll(application);
        session.setProperty(SESSION_OPTIONS_PROPERTY,
                lockTimeoutDisabled(application.getProperty(SESSION_OPTIONS_PROPERTY, "")));
        return session;
    }

    static String lockTimeoutDisabled(String options) {
        Matcher lockTimeout = LOCK_TIMEOUT_OPTION.matcher(options);
        if (lockTimeout.find()) {
            return lockTimeout.replaceAll(LOCK_TIMEOUT_DISABLED);
        }
        return options.isBlank() ? LOCK_TIMEOUT_DISABLED : options + " " + LOCK_TIMEOUT_DISABLED;
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
