package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

public final class ApplicationPoolCopy {

    private static final String APPLICATION_POOL_PROPERTIES = "spring.datasource.hikari";

    private ApplicationPoolCopy() {
    }

    public static HikariDataSource named(String poolName, HikariDataSource application, Environment environment) {
        HikariDataSource copy = new HikariDataSource();
        Binder.get(environment).bind(APPLICATION_POOL_PROPERTIES, Bindable.ofInstance(copy));
        SessionStartupOptions.copy(application, copy);
        copy.setJdbcUrl(application.getJdbcUrl());
        copy.setUsername(application.getUsername());
        copy.setPassword(application.getPassword());
        Optional.ofNullable(application.getDriverClassName()).ifPresent(copy::setDriverClassName);
        copy.setPoolName(poolName);
        return copy;
    }
}
