package ru.projectx.contracts.config.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutboxPropertiesTest {

    private static final String[] COMPLETE = {
        "projectx.outbox.publishing.interval=5s", "projectx.outbox.publishing.batch-size=100",
        "projectx.outbox.confirm-timeout=10s", "projectx.outbox.retry.backoff=1m,5m,15m",
        "projectx.outbox.retention.payload=30d", "projectx.outbox.retention.interval=1h",
        "projectx.outbox.retention.batch-size=500"};

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Outbox.class);

    @Test
    void completeConfiguration_bindsPublishingConfirmTimeoutRetryAndRetention() {
        contexts.withPropertyValues(COMPLETE).run(context -> {
            OutboxProperties outbox = context.getBean(OutboxProperties.class);

            assertThat(outbox.publishing()).isEqualTo(new OutboxProperties.Publishing(Duration.ofSeconds(5), 100));
            assertThat(outbox.confirmTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(outbox.retry().backoff())
                    .containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
            assertThat(outbox.retention())
                    .isEqualTo(new OutboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500));
        });
    }

    @Test
    void emptyBackoff_failsValidation() {
        contexts.withPropertyValues(COMPLETE).withPropertyValues("projectx.outbox.retry.backoff=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void zeroConfirmTimeout_failsValidation() {
        contexts.withPropertyValues(COMPLETE).withPropertyValues("projectx.outbox.confirm-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void ladder_givesPauseAfterEachFailedAttemptAndNothingBeyondIt() {
        OutboxProperties.Retry retry = new OutboxProperties.Retry(
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)));

        assertThat(retry.delayAfterFailedAttempt(1)).contains(Duration.ofMinutes(1));
        assertThat(retry.delayAfterFailedAttempt(2)).contains(Duration.ofMinutes(5));
        assertThat(retry.delayAfterFailedAttempt(3)).contains(Duration.ofMinutes(15));
        assertThat(retry.delayAfterFailedAttempt(4)).isEmpty();
        assertThat(retry.delayAfterFailedAttempt(0)).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxProperties.class)
    static class Outbox {
    }
}
