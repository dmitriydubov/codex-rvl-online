package ru.projectx.datareference.config.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReferenceCacheNotificationPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationPropertiesBinding.class);

    @Test
    void defaults_reconnectAfterOneSecond_andWaitAtMostTenSecondsBetweenAttempts() {
        context.run(started -> {
            ReferenceCacheNotificationProperties properties =
                    started.getBean(ReferenceCacheNotificationProperties.class);
            assertThat(properties.reconnectDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.reconnectMaxDelay()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void maxDelayShorterThanDelay_failsStartup() {
        context.withPropertyValues(
                        "projectx.cache.notifications.reconnect-delay=5s",
                        "projectx.cache.notifications.reconnect-max-delay=4s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("maxDelayNotShorterThanDelay"));
    }

    @Test
    void maxDelayEqualToDelay_isAccepted() {
        context.withPropertyValues(
                        "projectx.cache.notifications.reconnect-delay=5s",
                        "projectx.cache.notifications.reconnect-max-delay=5s")
                .run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void zeroDelay_failsStartup() {
        context.withPropertyValues("projectx.cache.notifications.reconnect-delay=0s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("reconnectDelay"));
    }

    @EnableConfigurationProperties(ReferenceCacheNotificationProperties.class)
    static class NotificationPropertiesBinding {
    }
}
