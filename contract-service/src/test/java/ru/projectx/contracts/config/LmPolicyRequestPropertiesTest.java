package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LmPolicyRequestPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Requests.class);

    @Test
    void completeConfiguration_bindsIntervalAndBatchSize() {
        runner.withPropertyValues(
                "projectx.lm-policy-requests.interval=10s",
                "projectx.lm-policy-requests.batch-size=50")
                .run(context -> {
                    LmPolicyRequestProperties properties = context.getBean(LmPolicyRequestProperties.class);
                    assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.batchSize()).isEqualTo(50);
                });
    }

    @Test
    void zeroBatch_failsValidation() {
        runner.withPropertyValues(
                "projectx.lm-policy-requests.interval=10s",
                "projectx.lm-policy-requests.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingInterval_failsValidation() {
        runner.withPropertyValues("projectx.lm-policy-requests.batch-size=50")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(LmPolicyRequestProperties.class)
    static class Requests {
    }
}
