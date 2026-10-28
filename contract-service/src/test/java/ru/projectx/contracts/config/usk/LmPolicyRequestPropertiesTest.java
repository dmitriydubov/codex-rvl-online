package ru.projectx.contracts.config.usk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LmPolicyRequestPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Requests.class);

    private static final String INTERVAL = "projectx.lm-policy-requests.interval=10s";
    private static final String BATCH = "projectx.lm-policy-requests.batch-size=50";
    private static final String AWAITING_INTERVAL = "projectx.lm-policy-requests.awaiting-interval=1m";
    private static final String AWAITING_THRESHOLD = "projectx.lm-policy-requests.awaiting-threshold=1h";

    @Test
    void completeConfiguration_bindsPreparationAndAwaitingSettings() {
        runner.withPropertyValues(INTERVAL, BATCH, AWAITING_INTERVAL, AWAITING_THRESHOLD)
                .run(context -> {
                    LmPolicyRequestProperties properties = context.getBean(LmPolicyRequestProperties.class);
                    assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.batchSize()).isEqualTo(50);
                    assertThat(properties.awaitingInterval()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.awaitingThreshold()).isEqualTo(Duration.ofHours(1));
                });
    }

    @Test
    void zeroBatch_failsValidation() {
        runner.withPropertyValues(INTERVAL, "projectx.lm-policy-requests.batch-size=0", AWAITING_INTERVAL,
                AWAITING_THRESHOLD).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingInterval_failsValidation() {
        runner.withPropertyValues(BATCH, AWAITING_INTERVAL, AWAITING_THRESHOLD)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingAwaitingInterval_failsValidation() {
        runner.withPropertyValues(INTERVAL, BATCH, AWAITING_THRESHOLD)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingAwaitingThreshold_failsValidation() {
        runner.withPropertyValues(INTERVAL, BATCH, AWAITING_INTERVAL)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void awaitingThresholdBelowASecond_failsValidation() {
        runner.withPropertyValues(INTERVAL, BATCH, AWAITING_INTERVAL,
                "projectx.lm-policy-requests.awaiting-threshold=999ms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void zeroAwaitingInterval_failsValidation() {
        runner.withPropertyValues(INTERVAL, BATCH, "projectx.lm-policy-requests.awaiting-interval=0s",
                AWAITING_THRESHOLD).run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(LmPolicyRequestProperties.class)
    static class Requests {
    }
}
