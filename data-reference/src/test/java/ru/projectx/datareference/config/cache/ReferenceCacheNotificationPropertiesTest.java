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
    void defaults_reconnectFromOneToTenSecondsInSeriesOfTen_andCheckSilentConnectionAfterMinute() {
        context.run(started -> {
            ReferenceCacheNotificationProperties properties =
                    started.getBean(ReferenceCacheNotificationProperties.class);
            assertThat(properties.reconnectDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.reconnectMaxDelay()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.reconnectRetries()).isEqualTo(10);
            assertThat(properties.silenceCheck()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    void zeroDelay_failsStartup() {
        assertStartupFailsOn("reconnect-delay=0s", "reconnectDelay");
    }

    @Test
    void zeroRetries_failsStartup() {
        assertStartupFailsOn("reconnect-retries=0", "reconnectRetries");
    }

    @Test
    void silenceCheckUnderSecond_failsStartup() {
        assertStartupFailsOn("silence-check=999ms", "silenceCheck");
    }

    private void assertStartupFailsOn(String setting, String property) {
        context.withPropertyValues("projectx.cache.notifications." + setting)
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(property));
    }

    @EnableConfigurationProperties(ReferenceCacheNotificationProperties.class)
    static class NotificationPropertiesBinding {
    }
}
