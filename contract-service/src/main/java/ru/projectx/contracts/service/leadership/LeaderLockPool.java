package ru.projectx.contracts.service.leadership;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.jdbc.DataSourceUnwrapper;

public final class LeaderLockPool implements LeaderLockConnections, AutoCloseable {

    static final String POOL_NAME = "leader-lock";
    static final String SESSION_OPTIONS_PROPERTY = "options";
    static final String LOCK_TIMEOUT_DISABLED = "-c lock_timeout=0";
    private static final Pattern LOCK_TIMEOUT_OPTION = Pattern.compile("-c\\s+lock_timeout=\\S+");
    private static final String[] NOT_COPIED =
            {"poolName", "maximumPoolSize", "minimumIdle", "logWriter", "loginTimeout"};

    private final HikariDataSource pool;

    private LeaderLockPool(HikariDataSource pool) {
        this.pool = pool;
    }

    public static LeaderLockPool besideApplicationPool(DataSource application, int connections, MeterRegistry registry) {
        HikariDataSource source = applicationPool(application);
        HikariDataSource pool = new HikariDataSource();
        BeanUtils.copyProperties(source, pool, NOT_COPIED);
        pool.setPoolName(POOL_NAME);
        pool.setMaximumPoolSize(connections);
        pool.setMinimumIdle(connections);
        pool.addDataSourceProperty(SESSION_OPTIONS_PROPERTY,
                lockTimeoutDisabled(source.getDataSourceProperties().getProperty(SESSION_OPTIONS_PROPERTY, "")));
        pool.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
        return new LeaderLockPool(pool);
    }

    private static HikariDataSource applicationPool(DataSource application) {
        return Optional.ofNullable(DataSourceUnwrapper.unwrap(application, HikariConfigMXBean.class, HikariDataSource.class))
                .orElseThrow(() -> new IllegalStateException(
                        "пул соединений приложения не HikariCP: " + application.getClass().getName()));
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
