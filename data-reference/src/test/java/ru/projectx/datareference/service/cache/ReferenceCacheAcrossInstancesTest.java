package ru.projectx.datareference.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.DataReferenceApplication;
import ru.projectx.datareference.config.cache.ReferenceCaches;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.service.inbound.OneCMessageApplied;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ReferenceCacheAcrossInstancesTest {

    private static final String KEY = "ключ";
    private static final String ANSWER = "ответ";
    private static final Duration NOTIFICATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(30);
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
        transactions.executeWithoutResult(status -> events.publishEvent(new OneCMessageApplied(OneCStream.MKB10)));

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
        transactions.executeWithoutResult(status -> events.publishEvent(new OneCMessageApplied(OneCStream.MKB10)));

        await().atMost(NOTIFICATION_TIMEOUT).until(() -> secondInstanceCache(ReferenceCaches.MKB10_CODES).get(KEY) == null);
        assertThat(secondInstanceCache(ReferenceCaches.INSURANCE_CONTRACTS_BY_ID).get(KEY)).isNotNull();
    }

    @Test
    void lostListenerConnection_clearsEveryCacheOfSecondInstanceAfterReconnect() {
        jdbc().sql(TERMINATE_LISTENER_SESSIONS)
                .param("name", ReferenceCacheNotificationsConfiguration.LISTENER_APPLICATION_NAME)
                .query()
                .listOfRows();

        await().atMost(RECONNECT_TIMEOUT).until(() -> ReferenceCaches.ALL.stream()
                .allMatch(name -> secondInstanceCache(name).get(KEY) == null));
        await().atMost(RECONNECT_TIMEOUT).until(() -> listenerSessions() == listenersWithoutSecondInstance + 1);
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
        return Objects.requireNonNull(secondInstance.getBean(CacheManager.class).getCache(name), name);
    }

    private long listenerSessions() {
        return jdbc().sql(LISTENER_SESSIONS)
                .param("name", ReferenceCacheNotificationsConfiguration.LISTENER_APPLICATION_NAME)
                .query(Long.class)
                .single();
    }

    private JdbcClient jdbc() {
        return JdbcClient.create(dataSource);
    }

    private ConfigurableApplicationContext start() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", environment.getRequiredProperty("spring.datasource.url"));
        properties.put("spring.datasource.username", environment.getRequiredProperty("spring.datasource.username"));
        properties.put("spring.datasource.password", environment.getRequiredProperty("spring.datasource.password"));
        properties.put("spring.autoconfigure.exclude",
                "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration");
        properties.put("spring.main.web-application-type", "none");
        properties.put("projectx.scheduling.enabled", false);
        properties.put("projectx.leadership.group", "instances-" + UUID.randomUUID());
        properties.put("projectx.cache.notifications.reconnect-delay", "100ms");
        properties.put("projectx.cache.notifications.reconnect-max-delay", "500ms");
        String[] arguments = properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(DataReferenceApplication.class).run(arguments);
    }
}
