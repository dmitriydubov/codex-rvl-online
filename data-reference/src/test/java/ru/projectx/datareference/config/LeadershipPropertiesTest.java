package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LeadershipPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(LeadershipPropertiesBinding.class);

    @Test
    void defaults_holdTheLockThirtySecondsAndRenewItEverySecond() {
        context.run(started -> {
            LeadershipProperties properties = started.getBean(LeadershipProperties.class);
            assertThat(properties.group()).isEqualTo("data-reference");
            assertThat(properties.lockTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.heartbeat()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void lockTtlNotLongerThanHeartbeat_failsStartup() {
        context.withPropertyValues("projectx.leadership.lock-ttl=2s", "projectx.leadership.heartbeat=2s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("lockTtlLongerThanHeartbeat"));
    }

    @Test
    void blankGroup_failsStartup() {
        context.withPropertyValues("projectx.leadership.group= ")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("group"));
    }

    @EnableConfigurationProperties(LeadershipProperties.class)
    static class LeadershipPropertiesBinding {
    }
}
