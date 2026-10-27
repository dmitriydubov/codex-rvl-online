package ru.projectx.datareference.config.restread;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.integration.jdbc.channel.PostgresChannelMessageTableSubscriber;
import ru.projectx.datareference.config.leadership.LeaderLockPoolConfig;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.ServiceInstances;

@IntegrationTest
class PoolShutdownTest {

    @Autowired
    private Environment environment;

    @Test
    void instanceStop_closesAllPools_andStopsTheCacheListener() {
        ConfigurableApplicationContext instance = ServiceInstances.startWithoutBroker(
                environment, environment.getRequiredProperty("spring.datasource.url"), Map.of());
        HikariDataSource applicationPool = instance.getBean(ApplicationPool.class).pool();
        HikariDataSource restReadPool = instance.getBean(RestReadPoolConfig.REST_READ_POOL, HikariDataSource.class);
        HikariDataSource leaderLockPool = instance.getBean(LeaderLockPoolConfig.LEADER_LOCK_POOL, HikariDataSource.class);
        PostgresChannelMessageTableSubscriber listener = instance.getBean(PostgresChannelMessageTableSubscriber.class);

        instance.close();

        assertThat(applicationPool.isClosed()).as("пул приложения закрыт").isTrue();
        assertThat(restReadPool.isClosed()).as("пул REST-чтений закрыт").isTrue();
        assertThat(leaderLockPool.isClosed()).as("пул блокировки ведущего закрыт").isTrue();
        assertThat(listener.isRunning()).as("слушатель сброса кэша остановлен").isFalse();
    }
}
