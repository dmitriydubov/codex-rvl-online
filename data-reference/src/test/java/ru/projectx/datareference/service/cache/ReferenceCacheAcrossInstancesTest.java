package ru.projectx.datareference.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.config.cache.ReconnectingListenerConnections;
import ru.projectx.datareference.config.cache.ReferenceCacheListenerConfig;
import ru.projectx.datareference.config.cache.ReferenceCaches;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.service.inbound.OneCMessageApplied;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.FreezableTcpProxy;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.ServiceInstances;

@IntegrationTest
class ReferenceCacheAcrossInstancesTest {

    private static final String KEY = "ключ";
    private static final String ANSWER = "ответ";
    private static final Duration NOTIFICATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final String JDBC_PREFIX = "jdbc:";
    private static final String LOOPBACK = "127.0.0.1";
    private static final String CONNECTED_GAUGE = "reference.cache.notifications.connected";
    private static final String LISTENER_SESSIONS = """
            select count(*) from pg_stat_activity where application_name = :name
            """;
    private static final String TERMINATE_LISTENER_SESSIONS = """
            select pg_terminate_backend(pid) from pg_stat_activity where application_name = :name
            """;

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private MeterRegistry meters;

    private ConfigurableApplicationContext secondInstance;
    private long listenersWithoutSecondInstance;

    @BeforeEach
    void secondInstanceHoldsEveryAnswer() {
        databaseCleaner.clear();
        listenersWithoutSecondInstance = listenerSessions();
        secondInstance = start();
        await().atMost(NOTIFICATION_TIMEOUT).until(() -> listenerSessions() == listenersWithoutSecondInstance + 1);
        ReferenceCaches.ALL.forEach(name -> secondInstanceCache(name).put(KEY, ANSWER));
    }

    @AfterEach
    void stopSecondInstance() {
        secondInstance.close();
    }

    @Test
    void messageAppliedHere_clearsItsStreamCachesOnSecondInstance_leavesTheRest() {
        commitApplied(OneCStream.MKB10);

        await().atMost(NOTIFICATION_TIMEOUT).until(() -> secondInstanceCache(ReferenceCaches.MKB10_CODES).get(KEY) == null);
        assertThat(secondInstanceCache(ReferenceCaches.MKB10_SEARCH).get(KEY)).isNull();
        assertThat(secondInstanceCache(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID).get(KEY)).isNotNull();
        assertThat(secondInstanceCache(ReferenceCaches.LPU_CATALOG).get(KEY)).isNotNull();
    }

    @Test
    void rolledBackMessage_doesNotNotify_whileLaterCommittedOneDoes() {
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new OneCMessageApplied(OneCStream.INSURANCE_CONTRACTS));
            status.setRollbackOnly();
        });
        commitApplied(OneCStream.MKB10);

        await().atMost(NOTIFICATION_TIMEOUT).until(() -> secondInstanceCache(ReferenceCaches.MKB10_CODES).get(KEY) == null);
        assertThat(secondInstanceCache(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID).get(KEY)).isNotNull();
    }

    @Test
    void lostListenerConnection_clearsEveryCacheOfSecondInstanceAfterReconnect() {
        jdbc().sql(TERMINATE_LISTENER_SESSIONS)
                .param("name", ReferenceCacheListenerConfig.LISTENER_APPLICATION_NAME)
                .query()
                .listOfRows();

        await().atMost(RECONNECT_TIMEOUT).until(() -> ReferenceCaches.ALL.stream()
                .allMatch(name -> secondInstanceCache(name).get(KEY) == null));
        await().atMost(RECONNECT_TIMEOUT).until(() -> listenerSessions() == listenersWithoutSecondInstance + 1);
    }

    @Test
    void everyOtherInstance_clearsItsStreamCaches() {
        ConfigurableApplicationContext thirdInstance = start();
        try {
            await().atMost(NOTIFICATION_TIMEOUT)
                    .until(() -> listenerSessions() == listenersWithoutSecondInstance + 2);
            cacheOf(thirdInstance, ReferenceCaches.PROGRAMS).put(KEY, ANSWER);

            commitApplied(OneCStream.PROGRAMS);

            await().atMost(NOTIFICATION_TIMEOUT).until(() -> cacheOf(thirdInstance, ReferenceCaches.PROGRAMS).get(KEY) == null
                    && secondInstanceCache(ReferenceCaches.PROGRAMS).get(KEY) == null);
        } finally {
            thirdInstance.close();
        }
    }

    @Test
    void sameStreamTwiceInOneTransaction_isDeliveredOnce() {
        AtomicInteger mkb10 = countNotifications(secondInstance, OneCStream.MKB10);
        AtomicInteger programs = countNotifications(secondInstance, OneCStream.PROGRAMS);

        commitApplied(OneCStream.MKB10, OneCStream.MKB10);
        commitApplied(OneCStream.PROGRAMS);

        await().atMost(NOTIFICATION_TIMEOUT).until(() -> programs.get() == 1);
        assertThat(mkb10).hasValue(1);
    }

    @Test
    void hungListenerConnection_isFoundBySilenceCheck_andEveryCacheIsClearedAfterReconnect() throws Exception {
        URI database = URI.create(databaseUrl().substring(JDBC_PREFIX.length()));
        try (FreezableTcpProxy proxy = FreezableTcpProxy.to(database.getHost(), database.getPort())) {
            String proxiedUrl = JDBC_PREFIX + database.getScheme() + "://" + LOOPBACK + ":" + proxy.port()
                    + database.getRawPath() + Optional.ofNullable(database.getRawQuery()).map(query -> "?" + query).orElse("");
            ConfigurableApplicationContext hungInstance = start(proxiedUrl,
                    Map.of("projectx.cache.notifications.silence-check", "1s"));
            try {
                ReconnectingListenerConnections listener = hungInstance.getBean(ReconnectingListenerConnections.class);
                await().atMost(NOTIFICATION_TIMEOUT).until(listener::connected);
                cacheOf(hungInstance, ReferenceCaches.LPU_CARD).put(KEY, ANSWER);

                proxy.freeze();
                await().atMost(RECONNECT_TIMEOUT).until(() -> !listener.connected());
                proxy.thaw();

                await().atMost(RECONNECT_TIMEOUT).until(listener::connected);
                await().atMost(NOTIFICATION_TIMEOUT).until(() -> cacheOf(hungInstance, ReferenceCaches.LPU_CARD).get(KEY) == null);
            } finally {
                proxy.thaw();
                hungInstance.close();
            }
        }
    }

    @Test
    void listenerGauge_showsConnectedInstance() {
        assertThat(meters.get(CONNECTED_GAUGE).gauge().value()).isEqualTo(1);
    }

    @Test
    void stoppedInstance_closesItsListenerConnection() {
        PostgresChannelMessageTableSubscriber subscriber =
                secondInstance.getBean(PostgresChannelMessageTableSubscriber.class);

        secondInstance.close();

        assertThat(subscriber.isRunning()).isFalse();
        await().atMost(NOTIFICATION_TIMEOUT).until(() -> listenerSessions() == listenersWithoutSecondInstance);
    }

    private Cache secondInstanceCache(String name) {
        return cacheOf(secondInstance, name);
    }

    private static Cache cacheOf(ConfigurableApplicationContext instance, String name) {
        return Objects.requireNonNull(instance.getBean(CacheManager.class).getCache(name), name);
    }

    private String databaseUrl() {
        return environment.getRequiredProperty("spring.datasource.url");
    }

    private static AtomicInteger countNotifications(ConfigurableApplicationContext instance, OneCStream stream) {
        AtomicInteger received = new AtomicInteger();
        instance.getBean(PostgresChannelMessageTableSubscriber.class).subscribe(
                new PostgresChannelMessageTableSubscriber.Subscription() {
                    @Override
                    public void notifyUpdate() {
                        received.incrementAndGet();
                    }

                    @Override
                    public String getRegion() {
                        return ReferenceCacheChannel.REGION;
                    }

                    @Override
                    public Object getGroupId() {
                        return stream.name();
                    }
                });
        return received;
    }

    private void commitApplied(OneCStream... streams) {
        transactions.executeWithoutResult(status -> {
            for (OneCStream stream : streams) {
                events.publishEvent(new OneCMessageApplied(stream));
            }
        });
    }

    private long listenerSessions() {
        return jdbc().sql(LISTENER_SESSIONS)
                .param("name", ReferenceCacheListenerConfig.LISTENER_APPLICATION_NAME)
                .query(Long.class)
                .single();
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }

    private ConfigurableApplicationContext start() {
        return start(databaseUrl(), Map.of());
    }

    private ConfigurableApplicationContext start(String databaseUrl, Map<String, Object> overrides) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("projectx.cache.notifications.reconnect-delay", "100ms");
        properties.put("projectx.cache.notifications.reconnect-max-delay", "500ms");
        properties.putAll(overrides);
        return ServiceInstances.startWithoutBroker(environment, databaseUrl, properties);
    }
}
