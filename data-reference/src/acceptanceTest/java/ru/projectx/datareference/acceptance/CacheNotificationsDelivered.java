package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import org.springframework.integration.util.UUIDConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.projectx.datareference.service.cache.ReferenceCacheChannel;

@Component
public class CacheNotificationsDelivered {

    private static final String PROBE_GROUP = "acceptance-probe";
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final String NOTIFY = "select pg_notify(:channel, :payload)";

    private final JdbcClient jdbc;
    private final AtomicLong probesReceived = new AtomicLong();

    public CacheNotificationsDelivered(PostgreSQLContainer database, PostgresChannelMessageTableSubscriber subscriber) {
        this.jdbc = JdbcClient.create(new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
        subscriber.subscribe(new Probe());
    }

    public void awaitDelivered() {
        long before = probesReceived.get();
        jdbc.sql(NOTIFY)
                .param("channel", ReferenceCacheChannel.channel())
                .param("payload", ReferenceCacheChannel.REGION + " " + UUIDConverter.getUUID(PROBE_GROUP))
                .query()
                .singleRow();
        await().atMost(DELIVERY_TIMEOUT).until(() -> probesReceived.get() > before);
    }

    private final class Probe implements PostgresChannelMessageTableSubscriber.Subscription {

        @Override
        public void notifyUpdate() {
            probesReceived.incrementAndGet();
        }

        @Override
        public String getRegion() {
            return ReferenceCacheChannel.REGION;
        }

        @Override
        public Object getGroupId() {
            return PROBE_GROUP;
        }
    }
}
