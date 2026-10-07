package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.Properties;

/**
 * Сборка пула REST-чтений по образцу пула приложения.
 *
 * <p>Адрес, учётные данные, параметры драйвера (в том числе таймауты сеанса PostgreSQL) и проверка соединений —
 * те же, что у пула приложения: так их не нужно задавать дважды, и в тестах пул REST-чтений смотрит туда же,
 * куда пул приложения. Свои у него имя, размер, ожидание соединения, сетевой тайм-аут чтения и ожидание отправки
 * отмены запроса.
 *
 * <p>Настройки переносятся перечнем, а не копией всего пула: копия HikariCP отдала бы обоим пулам один и тот же
 * набор параметров драйвера, и сетевой тайм-аут REST-чтений достался бы разбору. Новая настройка пула приложения,
 * нужная и REST-чтениям, добавляется в перечень.
 */
public final class RestReadPools {

    /** Имя пула в журнале HikariCP и в метке {@code pool} его метрик. */
    public static final String POOL_NAME = "rest-read";

    /** Сетевой тайм-аут чтения драйвера PostgreSQL, в секундах. */
    private static final String SOCKET_TIMEOUT = "socketTimeout";

    /** Сколько секунд драйвер PostgreSQL ждёт отправки отмены запроса. */
    private static final String CANCEL_SIGNAL_TIMEOUT = "cancelSignalTimeout";

    private RestReadPools() {
    }

    /**
     * Пул REST-чтений. Он ещё не открыт: соединения появятся при первом REST-чтении.
     *
     * @param application пул приложения с уже заданными настройками
     * @param settings    свои настройки пула REST-чтений
     * @return новый пул
     */
    public static HikariDataSource restReadPool(HikariDataSource application, RestReadPoolProperties settings) {
        HikariDataSource restRead = new HikariDataSource();
        connectLike(application, restRead);
        checkConnectionsLike(application, restRead);
        restRead.setPoolName(POOL_NAME);
        restRead.setMaximumPoolSize(settings.maximumPoolSize());
        restRead.setConnectionTimeout(settings.connectionTimeout().toMillis());
        restRead.setDataSourceProperties(driverProperties(application, settings));
        return restRead;
    }

    private static void connectLike(HikariDataSource application, HikariDataSource restRead) {
        restRead.setJdbcUrl(application.getJdbcUrl());
        restRead.setUsername(application.getUsername());
        restRead.setPassword(application.getPassword());
        Optional.ofNullable(application.getDriverClassName()).ifPresent(restRead::setDriverClassName);
        restRead.setSchema(application.getSchema());
        restRead.setTransactionIsolation(application.getTransactionIsolation());
        restRead.setAutoCommit(application.isAutoCommit());
        restRead.setConnectionInitSql(application.getConnectionInitSql());
    }

    private static void checkConnectionsLike(HikariDataSource application, HikariDataSource restRead) {
        restRead.setValidationTimeout(application.getValidationTimeout());
        restRead.setConnectionTestQuery(application.getConnectionTestQuery());
        restRead.setIdleTimeout(application.getIdleTimeout());
        restRead.setMaxLifetime(application.getMaxLifetime());
        restRead.setKeepaliveTime(application.getKeepaliveTime());
    }

    private static Properties driverProperties(HikariDataSource application, RestReadPoolProperties settings) {
        Properties properties = new Properties();
        properties.putAll(application.getDataSourceProperties());
        properties.put(SOCKET_TIMEOUT, Integer.toString(settings.socketTimeoutSeconds()));
        properties.put(CANCEL_SIGNAL_TIMEOUT, Integer.toString(settings.cancelSignalTimeoutSeconds()));
        return properties;
    }
}
