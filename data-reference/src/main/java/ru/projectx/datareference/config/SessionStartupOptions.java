package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;

public final class SessionStartupOptions {

    private static final String DRIVER_PROPERTY = "options";

    private SessionStartupOptions() {
    }

    public static void set(HikariDataSource pool, DatabaseSessionTimeouts timeouts) {
        pool.addDataSourceProperty(DRIVER_PROPERTY, timeouts.startupOptions());
    }

    public static void copy(HikariDataSource source, HikariDataSource target) {
        Optional.ofNullable(source.getDataSourceProperties().get(DRIVER_PROPERTY))
                .ifPresent(options -> target.addDataSourceProperty(DRIVER_PROPERTY, options));
    }
}
