package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LeaderLockPoolPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LeaderLockPool.class);

    @Test
    void poolSize_isBound() {
        runner.withPropertyValues("projectx.database.leader-lock-pool.maximum-pool-size=2").run(context ->
                assertThat(context.getBean(LeaderLockPoolProperties.class).maximumPoolSize()).isEqualTo(2));
    }

    @Test
    void poolWithoutConnections_failsValidation() {
        runner.withPropertyValues("projectx.database.leader-lock-pool.maximum-pool-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(LeaderLockPoolProperties.class)
    static class LeaderLockPool {
    }
}
