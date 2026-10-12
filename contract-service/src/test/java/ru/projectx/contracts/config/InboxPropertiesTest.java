package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class InboxPropertiesTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Inbox.class);

    @Test
    void completeConfiguration_bindsProcessingRetryRetentionAndNumbering() {
        contexts.withPropertyValues(
                "projectx.inbox.processing.interval=10s", "projectx.inbox.processing.batch-size=100",
                "projectx.inbox.retry.backoff=1m,5m,15m",
                "projectx.inbox.retention.payload=30d", "projectx.inbox.retention.interval=1h",
                "projectx.inbox.retention.batch-size=500", "projectx.inbox.requeue-delay=5s",
                "projectx.contracts.numbering.zone=Europe/Moscow").run(context -> {
            InboxProperties inbox = context.getBean(InboxProperties.class);

            assertThat(inbox.processing()).isEqualTo(new InboxProperties.Processing(Duration.ofSeconds(10), 100));
            assertThat(inbox.retry().backoff())
                    .containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
            assertThat(inbox.retention())
                    .isEqualTo(new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500));
            assertThat(inbox.requeueDelay()).isEqualTo(Duration.ofSeconds(5));
            assertThat(context.getBean(ContractNumberingProperties.class).zone()).isEqualTo(ZoneId.of("Europe/Moscow"));
        });
    }

    @Test
    void emptyBackoff_failsValidation() {
        contexts.withPropertyValues(
                "projectx.inbox.processing.interval=10s", "projectx.inbox.processing.batch-size=100",
                "projectx.inbox.retry.backoff=",
                "projectx.inbox.retention.payload=30d", "projectx.inbox.retention.interval=1h",
                "projectx.inbox.retention.batch-size=500", "projectx.inbox.requeue-delay=5s",
                "projectx.contracts.numbering.zone=Europe/Moscow")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void ladder_givesPauseAfterEachFailedAttemptAndNothingBeyondIt() {
        InboxProperties.Retry retry = new InboxProperties.Retry(
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)));

        assertThat(retry.delayAfterFailedAttempt(1)).contains(Duration.ofMinutes(1));
        assertThat(retry.delayAfterFailedAttempt(3)).contains(Duration.ofMinutes(15));
        assertThat(retry.delayAfterFailedAttempt(4)).isEmpty();
        assertThat(retry.delayAfterFailedAttempt(0)).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({InboxProperties.class, ContractNumberingProperties.class})
    static class Inbox {
    }
}
