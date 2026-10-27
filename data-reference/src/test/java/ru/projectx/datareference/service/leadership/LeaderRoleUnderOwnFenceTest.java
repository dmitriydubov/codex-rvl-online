package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import ru.projectx.datareference.config.LeadershipProperties;
import ru.projectx.datareference.config.restread.RestReadDataSource;
import ru.projectx.datareference.support.ExhaustedPool;
import ru.projectx.datareference.support.HeldLeaderFence;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.RoleRevocations;

@IntegrationTest
@TestPropertySource(properties = "projectx.database.session-timeouts.lock-timeout=1s")
class LeaderRoleUnderOwnFenceTest {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MARGIN = Duration.ofSeconds(2);

    @Autowired
    private BackgroundLeadership leadership;

    @Autowired
    private LeadershipFence fence;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LeadershipProperties properties;

    @Autowired
    private RoleRevocations revocations;

    @BeforeEach
    void leaderWithoutRevocations() {
        await().atMost(MARGIN).until(leadership::leader);
        revocations.reset();
    }

    @Test
    void ownTransactionHoldingTheFenceLongerThanLockTimeoutAndHeartbeat_keepsTheRole() throws Exception {
        Duration longerThanLockWaitAndRenewal = LOCK_TIMEOUT.plus(properties.heartbeat()).plus(MARGIN);

        try (HeldLeaderFence ignored = HeldLeaderFence.open(transactions, fence)) {
            await().during(longerThanLockWaitAndRenewal).atMost(longerThanLockWaitAndRenewal.plus(MARGIN))
                    .until(leadership::leader);
        }

        assertThat(revocations.count()).as("снятий роли за время удержания блокировки").isZero();
        assertThat(leadership.leader()).isTrue();
    }

    @Test
    void applicationPoolBusyLongerThanConnectionWaitAndHeartbeat_keepsTheRole() throws Exception {
        HikariDataSource applicationPool = dataSource.unwrap(RestReadDataSource.class).applicationPool();
        Duration longerThanConnectionWaitAndRenewal = Duration.ofMillis(applicationPool.getConnectionTimeout())
                .plus(properties.heartbeat()).plus(MARGIN);

        try (ExhaustedPool ignored = ExhaustedPool.of(dataSource)) {
            await().during(longerThanConnectionWaitAndRenewal).atMost(longerThanConnectionWaitAndRenewal.plus(MARGIN))
                    .until(leadership::leader);
        }

        assertThat(revocations.count()).as("снятий роли за время занятости пула приложения").isZero();
        assertThat(leadership.leader()).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RevocationsConfig {

        @Bean
        RoleRevocations roleRevocations() {
            return new RoleRevocations();
        }
    }
}
