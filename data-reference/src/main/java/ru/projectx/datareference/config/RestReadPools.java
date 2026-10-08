package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Сборка пула REST-чтений по образцу пула приложения: те же свойства {@code spring.datasource.hikari.*}, а адрес и
 * учётные данные берутся у готового пула, потому что Boot мог взять их из окружения, как в тестах. Свои у пула только
 * имя, размер, ожидание соединения и параметры драйвера, чтобы короткий тайм-аут не достался разбору.
 */
public final class RestReadPools {

    /** Имя пула в журнале HikariCP и в метке {@code pool} его метрик. */
    public static final String POOL_NAME = "rest-read";

    private static final String APPLICATION_POOL_PROPERTIES = "spring.datasource.hikari";

    /** Сетевой тайм-аут чтения драйвера PostgreSQL, в секундах. */
    private static final String SOCKET_TIMEOUT = "socketTimeout";

    /** Сколько секунд драйвер PostgreSQL ждёт отправки отмены запроса. */
    private static final String CANCEL_SIGNAL_TIMEOUT = "cancelSignalTimeout";

    private RestReadPools() {
    }

    /** Новый пул ещё не открыт: соединения появятся при первом REST-чтении. */
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
