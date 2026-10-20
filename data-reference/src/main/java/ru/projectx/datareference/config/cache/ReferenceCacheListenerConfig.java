package ru.projectx.datareference.config.cache;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import javax.sql.DataSource;
import org.postgresql.jdbc.PgConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import ru.projectx.datareference.config.restread.RestReadDataSource;

@Configuration(proxyBeanMethods = false)
public class ReferenceCacheListenerConfig {

    public static final String LISTENER_APPLICATION_NAME = "data-reference-cache-notifications";

    static final String CONNECTED = "reference.cache.notifications.connected";

    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String APPLICATION_NAME = "ApplicationName";
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String LOGIN_TIMEOUT = "loginTimeout";
    private static final long MIN_TIMEOUT_SECONDS = 1;

    @Bean
    ReconnectingListenerConnections referenceCacheListenerConnections(DataSource dataSource,
            ReferenceCacheNotificationProperties properties) throws SQLException {

        return new ReconnectingListenerConnections(
                connectLike(dataSource.unwrap(RestReadDataSource.class).applicationPool()), properties);
    }

    @Bean
    MeterBinder referenceCacheListenerGauge(ReconnectingListenerConnections connections) {
        return registry -> Gauge.builder(CONNECTED, connections, listener -> listener.connected() ? 1 : 0)
                .description("1 — экземпляр слушает оповещения о сбросе кэша справочников; 0 — соединения нет")
                .register(registry);
    }

    private static PgConnectionSupplier connectLike(HikariDataSource application) {
        String url = application.getJdbcUrl();
        String timeoutSeconds = Long.toString(Math.max(MIN_TIMEOUT_SECONDS,
                Duration.ofMillis(application.getConnectionTimeout()).toSeconds()));
        Properties settings = new Properties();
        settings.putAll(application.getDataSourceProperties());
        settings.setProperty(USER, application.getUsername());
        settings.setProperty(PASSWORD, application.getPassword());
        settings.setProperty(APPLICATION_NAME, LISTENER_APPLICATION_NAME);
        settings.setProperty(CONNECT_TIMEOUT, timeoutSeconds);
        settings.setProperty(LOGIN_TIMEOUT, timeoutSeconds);
        return () -> DriverManager.getConnection(url, settings).unwrap(PgConnection.class);
    }
}
