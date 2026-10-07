package ru.projectx.datareference.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Сборка пула REST-чтений по образцу пула приложения.
 *
 * <p>Пул REST-чтений связывается с теми же свойствами {@code spring.datasource.hikari.*}, что и пул приложения, — так
 * же, как Spring Boot настраивает сам пул приложения. Поэтому у обоих одни параметры драйвера (в том числе таймауты
 * сеанса PostgreSQL), проверка соединений и остальные настройки пула, и новая настройка пула приложения сразу
 * достаётся и REST-чтениям. Адрес и учётные данные берутся у готового пула приложения: Spring Boot мог взять их не из
 * свойств, а из окружения (так подключаются тесты).
 *
 * <p>Поверх свои у пула REST-чтений имя, размер, ожидание соединения, сетевой тайм-аут чтения и ожидание отправки
 * отмены запроса. Параметры драйвера у пула свои, отдельным набором: сетевой тайм-аут REST-чтений не достаётся разбору.
 */
public final class RestReadPools {

    /** Имя пула в журнале HikariCP и в метке {@code pool} его метрик. */
    public static final String POOL_NAME = "rest-read";

    /** Свойства пула приложения в конфигурации Spring Boot. */
    private static final String APPLICATION_POOL_PROPERTIES = "spring.datasource.hikari";

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
     * @param environment конфигурация приложения со свойствами пула приложения
     * @param settings    свои настройки пула REST-чтений
     * @return новый пул
     */
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
