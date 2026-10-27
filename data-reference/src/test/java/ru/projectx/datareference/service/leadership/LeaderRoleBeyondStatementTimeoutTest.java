package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import ru.projectx.datareference.config.LeadershipProperties;
import ru.projectx.datareference.support.HeldLeaderFence;
import ru.projectx.datareference.support.IntegrationTest;
import ru.projectx.datareference.support.RoleRevocations;

@IntegrationTest
@TestPropertySource(properties = "projectx.database.session-timeouts.statement-timeout=1s")
class LeaderRoleBeyondStatementTimeoutTest {

    private static final Duration STATEMENT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MARGIN = Duration.ofSeconds(2);

    @Autowired
    private BackgroundLeadership leadership;

    @Autowired
    private LeadershipFence fence;

    @Autowired
    private PlatformTransactionManager transactions;

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
    void renewalCancelledByStatementTimeoutWhileOwnTransactionHoldsTheFence_isRetriedAndKeepsTheRole()
            throws Exception {

        Duration longerThanStatementTimeoutAndRenewal = STATEMENT_TIMEOUT.plus(properties.heartbeat()).plus(MARGIN);

        try (HeldLeaderFence ignored = HeldLeaderFence.open(transactions, fence)) {
            await().during(longerThanStatementTimeoutAndRenewal)
                    .atMost(longerThanStatementTimeoutAndRenewal.plus(MARGIN))
                    .until(leadership::leader);
        }

        assertThat(revocations.count()).as("снятий роли за время удержания блокировки").isZero();
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
