package ru.projectx.datareference.config.restread;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.env.Environment;
import ru.projectx.datareference.config.ApplicationPoolCopy;

public final class RestReadPools {

    public static final String POOL_NAME = "rest-read";

    private static final String SOCKET_TIMEOUT = "socketTimeout";

    private static final String CANCEL_SIGNAL_TIMEOUT = "cancelSignalTimeout";

    private RestReadPools() {
    }

    public static HikariDataSource restReadPool(
            HikariDataSource application, Environment environment, RestReadPoolProperties settings) {

        HikariDataSource restRead = ApplicationPoolCopy.named(POOL_NAME, application, environment);
        restRead.setMaximumPoolSize(settings.maximumPoolSize());
        restRead.setConnectionTimeout(settings.connectionTimeout().toMillis());
        restRead.addDataSourceProperty(SOCKET_TIMEOUT, Integer.toString(settings.socketTimeoutSeconds()));
        restRead.addDataSourceProperty(CANCEL_SIGNAL_TIMEOUT, Integer.toString(settings.cancelSignalTimeoutSeconds()));
        return restRead;
    }
}
