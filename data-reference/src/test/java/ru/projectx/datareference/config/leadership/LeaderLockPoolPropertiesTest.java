package ru.projectx.datareference.config.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LeaderLockPoolPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(LeaderLockPoolPropertiesBinding.class);

    @Test
    void configuredSize_isBound() {
        context.withPropertyValues("projectx.database.leader-lock-pool.maximum-pool-size=2")
                .run(started -> assertThat(started.getBean(LeaderLockPoolProperties.class).maximumPoolSize())
                        .isEqualTo(2));
    }

    @Test
    void poolWithoutConnections_failsStartup() {
        context.withPropertyValues("projectx.database.leader-lock-pool.maximum-pool-size=0")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("maximumPoolSize"));
    }

    @EnableConfigurationProperties(LeaderLockPoolProperties.class)
    static class LeaderLockPoolPropertiesBinding {
    }
}
