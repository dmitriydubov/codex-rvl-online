package ru.projectx.datareference.config.cache;

import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.postgresql.jdbc.PgConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.datareference.config.FlagGauges;
import ru.projectx.datareference.config.restread.RestReadDataSource;

@Configuration(proxyBeanMethods = false)
public class ReferenceCacheListenerConfig {

    public static final String LISTENER_APPLICATION_NAME = ListenerConnectionSettings.LISTENER_APPLICATION_NAME;

    static final String CONNECTED = "reference.cache.notifications.connected";

    private static final String CONNECTED_DESCRIPTION =
            "1 — экземпляр слушает оповещения о сбросе кэша справочников; 0 — соединения нет";

    @Bean
    ReconnectingListenerConnections referenceCacheListenerConnections(DataSource dataSource,
            ReferenceCacheNotificationProperties properties) throws SQLException {

        RestReadDataSource pools = dataSource.unwrap(RestReadDataSource.class);
        String url = pools.applicationPool().getJdbcUrl();
        Properties settings = ListenerConnectionSettings.like(pools.applicationPool());
        return new ReconnectingListenerConnections(
                () -> DriverManager.getConnection(url, settings).unwrap(PgConnection.class), properties);
    }

    @Bean
    MeterBinder referenceCacheListenerGauge(ReconnectingListenerConnections connections) {
        return registry -> FlagGauges.register(registry, CONNECTED, CONNECTED_DESCRIPTION, connections::connected);
    }
}
