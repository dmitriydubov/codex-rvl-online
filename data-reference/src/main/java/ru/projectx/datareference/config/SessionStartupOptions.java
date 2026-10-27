package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;

public final class SessionStartupOptions {

    private static final String DRIVER_PROPERTY = "options";
    private static final String NOT_SET = "у пула %s нет таймаутов сеанса (options)";

    private SessionStartupOptions() {
    }

    public static void set(HikariDataSource pool, DatabaseSessionTimeouts timeouts) {
        pool.addDataSourceProperty(DRIVER_PROPERTY, timeouts.startupOptions());
    }

    public static boolean isSet(HikariDataSource pool) {
        return pool.getDataSourceProperties().containsKey(DRIVER_PROPERTY);
    }

    public static void copy(HikariDataSource source, HikariDataSource target) {
        Object options = Optional.ofNullable(source.getDataSourceProperties().get(DRIVER_PROPERTY))
                .orElseThrow(() -> new IllegalStateException(NOT_SET.formatted(source.getPoolName())));
        target.addDataSourceProperty(DRIVER_PROPERTY, options);
    }
}
