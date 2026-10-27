package ru.projectx.datareference.config.restread;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.datareference.config.leadership.LeaderLockPoolConfig;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "spring.application.name=data-reference-pool-shutdown")
@DirtiesContext
class PoolShutdownTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Qualifier(LeaderLockPoolConfig.LEADER_LOCK_POOL)
    private HikariDataSource leaderLockPool;

    @Test
    void contextClose_closesAllPools() throws SQLException {
        RestReadDataSource pools = dataSource.unwrap(RestReadDataSource.class);

        context.getBeanFactory().destroySingletons();

        assertThat(pools.applicationPool().isClosed()).as("пул приложения закрыт").isTrue();
        assertThat(pools.restReadPool().isClosed()).as("пул REST-чтений закрыт").isTrue();
        assertThat(leaderLockPool.isClosed()).as("пул блокировки ведущего закрыт").isTrue();
    }
}
