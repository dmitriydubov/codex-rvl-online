package ru.projectx.datareference.config.restread;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

public final class RestReadPools {

    public static final String POOL_NAME = "rest-read";

    private static final String APPLICATION_POOL_PROPERTIES = "spring.datasource.hikari";

    private static final String SOCKET_TIMEOUT = "socketTimeout";

    private static final String CANCEL_SIGNAL_TIMEOUT = "cancelSignalTimeout";

    private RestReadPools() {
    }

    public static HikariDataSource restReadPool(
            HikariDataSource application, Environment environment, RestReadPoolProperties settings) {

        HikariDataSource restRead = new HikariDataSource();
        Binder.get(environment).bind(APPLICATION_POOL_PROPERTIES, Bindable.ofInstance(restRead));
        connectLike(application, restRead);
        restRead.setPoolName(POOL_NAME);
        restRead.setMaximumPoolSize(settings.maximumPoolSize());
        restRead.setConnectionTimeout(settings.connectionTimeout().toMillis());
        restRead.addDataSourceProperty(SOCKET_TIMEOUT, Integer.toString(settings.socketTimeoutSeconds()));
        restRead.addDataSourceProperty(CANCEL_SIGNAL_TIMEOUT, Integer.toString(settings.cancelSignalTimeoutSeconds()));
        return restRead;
    }

    private static void connectLike(HikariDataSource application, HikariDataSource restRead) {
        restRead.setJdbcUrl(application.getJdbcUrl());
        restRead.setUsername(application.getUsername());
        restRead.setPassword(application.getPassword());
        Optional.ofNullable(application.getDriverClassName()).ifPresent(restRead::setDriverClassName);
    }
}
