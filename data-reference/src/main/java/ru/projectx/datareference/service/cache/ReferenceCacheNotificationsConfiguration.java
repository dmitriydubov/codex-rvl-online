package ru.projectx.datareference.service.cache;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.postgresql.jdbc.PgConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import ru.projectx.datareference.config.cache.ReferenceCacheNotificationProperties;
import ru.projectx.datareference.config.restread.RestReadDataSource;
import ru.projectx.datareference.entity.inbound.OneCStream;

@Configuration(proxyBeanMethods = false)
public class ReferenceCacheNotificationsConfiguration {

    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String APPLICATION_NAME = "ApplicationName";
    static final String LISTENER_APPLICATION_NAME = "data-reference-cache-notifications";

    @Bean
    PostgresChannelMessageTableSubscriber referenceCacheSubscriber(DataSource dataSource,
            ReferenceCacheNotificationProperties properties, ReferenceCacheEviction eviction) throws SQLException {

        PgConnectionSupplier connect = connectLike(dataSource.unwrap(RestReadDataSource.class).applicationPool());
        PostgresChannelMessageTableSubscriber subscriber = new PostgresChannelMessageTableSubscriber(
                new ReconnectingListenerConnections(connect, properties), ReferenceCacheChannel.TABLE_PREFIX);
        for (OneCStream stream : OneCStream.values()) {
            subscriber.subscribe(new StreamCacheSubscription(stream, eviction));
        }
        return subscriber;
    }

    private static PgConnectionSupplier connectLike(HikariDataSource application) {
        String url = application.getJdbcUrl();
        Properties settings = new Properties();
        settings.putAll(application.getDataSourceProperties());
        settings.setProperty(USER, application.getUsername());
        settings.setProperty(PASSWORD, application.getPassword());
        settings.setProperty(APPLICATION_NAME, LISTENER_APPLICATION_NAME);
        return () -> DriverManager.getConnection(url, settings).unwrap(PgConnection.class);
    }
}
